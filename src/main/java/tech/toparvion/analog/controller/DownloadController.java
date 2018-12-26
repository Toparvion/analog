package tech.toparvion.analog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tech.toparvion.analog.model.config.nodes.Node;
import tech.toparvion.analog.model.config.nodes.NodesProperties;
import tech.toparvion.analog.service.DownloadRestTemplate;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static java.lang.Math.max;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.size;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.util.StringUtils.hasText;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

/**
 * A controller for downloading currently selected logs and retrieving their sizes. Designed to be used both from
 * client (browser) and from other servers in case of chained calls (for retrieving remote logs).
 *
 * @author Toparvion
 * @since v0.9
 */
@RestController
public class DownloadController {
  private static final Logger log = LoggerFactory.getLogger(DownloadController.class);
  private static final String DOWNLOAD_URI_PATH = "/download";
  /**
   * Charset to use for composing 'Content-Type' header. This is subject to improve as files with different encodings
   * may look corrupted after downloading with UTF-8.
   */
  private static final Charset DEFAULT_CHARSET = UTF_8;

  private final NodesProperties nodesProperties;
  private final DownloadRestTemplate downloadRestTemplate;

  @Autowired
  public DownloadController(NodesProperties nodesProperties) {
    this.nodesProperties = nodesProperties;
    this.downloadRestTemplate = new DownloadRestTemplate();
  }

  @RequestMapping(value = DOWNLOAD_URI_PATH, method = HEAD)
  public HttpHeaders getLogInfo(@RequestParam("path") String pathParam,
                                @RequestParam(value = "node", required = false) String nodeName)
      throws IOException {
    Node node = getNodeByName(nodeName);
    boolean isRemote = !node.equals(nodesProperties.getThis());
    long size, lastModified;
    String extendedPath;
    if (!isRemote) {
      Path path = Paths.get(denormalize(pathParam));
      log.debug("Local file size requested; Will query from path: {}", path);
      if (!isReadable(path)) {
        throw new IllegalArgumentException(format("Local file '%s' not found or is inaccessible for reading.", path));
      }
      size = size(path);
      lastModified = Files.getLastModifiedTime(path).toMillis();
      extendedPath = path.toAbsolutePath().toString();

    } else {
      URI uri = UriComponentsBuilder.newInstance()
          .scheme("http")
          .host(node.getHost())
          .port(node.getServerPort())
          .path(DOWNLOAD_URI_PATH)
          .queryParam("path", pathParam)
          .build()
          .toUri();
      log.debug("Remote file size requested. Will query from URI: HEAD {}", uri);
      HttpHeaders remoteHeaders = new RestTemplate().headForHeaders(uri);
      if (!remoteHeaders.containsKey(CONTENT_LENGTH)) {
        throw new IllegalStateException(format("No Content-Length received from uri=%s", uri));
      }
      size = remoteHeaders.getContentLength();
      lastModified = remoteHeaders.getLastModified();
      extendedPath = uri.toString();

    }
    log.debug("File '{}' has size {} bytes, last modified {}.", extendedPath, size, Instant.ofEpochMilli(lastModified));
    HttpHeaders answerHeaders = new HttpHeaders();
    answerHeaders.setContentLength(size);
    answerHeaders.setLastModified(lastModified);
    return answerHeaders;
  }

  @RequestMapping(value = DOWNLOAD_URI_PATH, method = GET)
  public ResponseEntity<? extends Resource> downloadLog(
      @RequestParam("path") String pathParam,
      @RequestParam(value = "node", required = false) String nodeName,
      @RequestParam(value = "last-kbytes", required = false, defaultValue = "0") int lastKBytes,
      HttpServletResponse response) throws IOException
  {
    Node node = getNodeByName(nodeName);
    boolean isRemote = !node.equals(nodesProperties.getThis());
    if (!isRemote) {
      Path path = Paths.get(denormalize(pathParam));
      log.debug("Local file requested. Retrieving it from path: {}", path);
      if (!isReadable(path)) {
        throw new IllegalArgumentException(format("Local file '%s' not found or is inaccessible for reading.", path));
      }
      long entireFileSize = size(path);               // it's just a snapshot of file size which can change immediately
      int lastBytes = lastKBytes << 10;               // 1 KByte = 2^10 bytes so it's enough to shift it left by 10
      long readStartPosition = (lastBytes > 0)
          ? max(0, (entireFileSize - lastBytes))      // 'max()' to prevent exceeding of actual file size
          : 0;
      if (readStartPosition == 0) {
        log.debug("Read start position is 0 so returning the file at whole...");
        Resource pathResource = new FileSystemResource(path);
        response.setHeader(CONTENT_DISPOSITION, format("attachment; filename=\"%s\"", path.getFileName().toString()));
        response.setHeader(CONTENT_TYPE, new MediaType(TEXT_PLAIN, DEFAULT_CHARSET).toString());
        return new ResponseEntity<>(pathResource, OK);

      } else {
        log.debug("Read start position is {} so returning files's tail only...", readStartPosition);
        InputStream fileInputStream = Files.newInputStream(path);
        long skipped = fileInputStream.skip(readStartPosition);
        if (skipped != readStartPosition) {
          log.warn("Actual skipped {} bytes while requested {} bytes.", skipped, readStartPosition);
        }
        InputStreamResource isr = new InputStreamResource(fileInputStream, format("resource based on '%s'", path));
        response.setHeader(CONTENT_DISPOSITION, format("attachment; filename=\"%s\"", path.getFileName().toString()));
        response.setHeader(CONTENT_LENGTH, String.valueOf(entireFileSize-skipped));
        response.setHeader(CONTENT_TYPE, new MediaType(TEXT_PLAIN, DEFAULT_CHARSET).toString());
        return new ResponseEntity<>(isr, OK);
      }
    }

    // if requested file is not local, delegate the request to corresponding node
    URI uri = UriComponentsBuilder.newInstance()
        .scheme("http")
        .host(node.getHost())
        .port(node.getServerPort())
        .path(DOWNLOAD_URI_PATH)
        .queryParam("path", pathParam)
        .queryParam("last-kbytes", lastKBytes)
        .build()
        .toUri();
    log.debug("Remote file requested. Retrieving it from URI: GET {}", uri);
    return downloadRestTemplate.getForEntity(uri, InputStreamResource.class);
  }

  @ExceptionHandler(IOException.class)
  @ResponseStatus(value = SERVICE_UNAVAILABLE)
  public void handleLocalFileAccessException(IOException fileAccessException) {
    log.error("Failed to access requested local file", fileAccessException);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(value = NOT_FOUND)
  public void handleLocalFileAbsence(IllegalArgumentException fileNotFoundException) {
    log.warn("Failed to download file: {} ", fileNotFoundException.getMessage());
  }

  @ExceptionHandler(HttpClientErrorException.class)
  public ResponseEntity handleRemote4xxError(HttpClientErrorException remoteException) {
    log.error("Failed to retrieve size of remote file because of HTTP error {} ({})", remoteException.getStatusCode(),
        hasText(remoteException.getStatusText()) ? remoteException.getStatusText() : "[no status text]");
    return ResponseEntity
        .status(remoteException.getStatusCode())
        .build();
  }

  @ExceptionHandler(RestClientException.class)
  @ResponseStatus(value = BAD_GATEWAY)
  public void handleRemoteCallException(RestClientException remoteCallException) {
    log.error("Failed to retrieve size of remote file due to unexpected exception.", remoteCallException);
  }

  private Node getNodeByName(@Nullable String nodeName) {
    return hasText(nodeName)
        ? nodesProperties.findNodeByName(nodeName)
        : nodesProperties.getThis();
  }

  private String denormalize(String pathParam) {
    if (pathParam.matches("^/\\w:.*")) {
      pathParam = pathParam.substring(1);
    }
    return pathParam;
  }

}
