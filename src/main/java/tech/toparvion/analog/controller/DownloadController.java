package tech.toparvion.analog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tech.toparvion.analog.model.config.ClusterNode;
import tech.toparvion.analog.model.config.ClusterProperties;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.nio.file.Files.isReadable;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.util.StringUtils.hasText;

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
  private static final String DOWNLOAD_SIZE_URI_PATH = "/download/size";

  private final ClusterProperties clusterProperties;

  @Autowired
  public DownloadController(ClusterProperties clusterProperties) {
    this.clusterProperties = clusterProperties;
  }

  @GetMapping(value = "/download", produces = TEXT_PLAIN_VALUE)
  public ResponseEntity<Resource> downloadCurrentLog(@RequestParam("path") String pathParam,
                                                     HttpServletResponse response) {
    Path path = Paths.get(denormalize(pathParam));
    if (!isReadable(path)) {
      log.warn("Local file '{}' not found.", pathParam);
      return new ResponseEntity<>(NOT_FOUND);
    }
    Resource logResource = new PathResource(path);
    response.setHeader(CONTENT_DISPOSITION, format("attachment; filename=\"%s\"", path.getFileName().toString()));
    return new ResponseEntity<>(logResource, OK);
  }

  @GetMapping(value = DOWNLOAD_SIZE_URI_PATH, produces = TEXT_PLAIN_VALUE)
  public String getLogCurrentSize(@RequestParam("path") String pathParam,
                                  @RequestParam(value = "node", required = false) String nodeName) throws IOException {
    ClusterNode node = hasText(nodeName)
        ? clusterProperties.findNodeByName(nodeName)
        : clusterProperties.getMyselfNode();

    boolean isRemote = !node.equals(clusterProperties.getMyselfNode());
    Long size;
    String extendedPath;
    if (isRemote) {
      RestTemplate restTemplate = new RestTemplate();
      URI uri = UriComponentsBuilder.newInstance()
          .scheme("http")
          .host(node.getHost())
          .port(clusterProperties.resolveServerPortFor(node))
          .path(DOWNLOAD_SIZE_URI_PATH)
          .queryParam("path", pathParam)
          .build()
          .toUri();
      log.debug("Remote log size requested. Will query from URI: {}", uri);
      String sizeStr = restTemplate.getForObject(uri, String.class);
      if (!hasText(sizeStr)) {
        throw new IllegalStateException(format("Empty result received from uri=%s", uri));
      }
      size = Long.valueOf(sizeStr);
      extendedPath = uri.toString();

    } else {
      Path path = Paths.get(denormalize(pathParam));
      if (!isReadable(path)) {
        throw new IllegalArgumentException(format("Local file '%s' not found or is inaccessible for reading.", path));
      }
      size = Files.size(path);
      extendedPath = path.toAbsolutePath().toString();
    }

    log.debug("At the time of request, file '{}' had size {} bytes.", extendedPath, size);
    return valueOf(size);
  }

  @ExceptionHandler(IOException.class)
  @ResponseStatus(value = SERVICE_UNAVAILABLE)
  public void handleLocalFileAccessException(IOException fileAccessException) {
    log.error("Failed to access requested local file", fileAccessException);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(value = NOT_FOUND)
  public void handleLocalFileAbsence(IllegalArgumentException fileNotFoundException) {
    log.error(fileNotFoundException.getMessage());
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

  private String denormalize(String pathParam) {
    if (pathParam.matches("^/\\w:.*")) {
      pathParam = pathParam.substring(1);
    }
    return pathParam;
  }

}
