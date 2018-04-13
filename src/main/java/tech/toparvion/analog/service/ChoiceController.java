package tech.toparvion.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.toparvion.analog.model.LogChoice;

import javax.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.String.format;
import static java.nio.file.Files.isReadable;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

@RestController
public class ChoiceController {
  private static final Logger log = LoggerFactory.getLogger(ChoiceController.class);

  private final LogChoicesProvider logChoicesProvider;

  @Autowired
  public ChoiceController(LogChoicesProvider logChoicesProvider) {
    this.logChoicesProvider = logChoicesProvider;
  }

  @GetMapping("/choices")
  public List<LogChoice> choices() {
/*
    Logger logger = LoggerFactory.getLogger(getClass());
    try {
      logger.info("I've come for Dnepr...");
      RestTemplate restTemplate = new RestTemplate();
      restTemplate.getForObject("http://analog.dnepr.ftc.ru:8085", String.class);
    } catch (RestClientException e) {
      logger.error("", e);
    }
*/
    return logChoicesProvider.provideLogChoices();
  }

  @GetMapping(value = "/download", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<Resource> downloadCurrentLog(@RequestParam("path") String pathParam,
                                                     HttpServletResponse response) {
    if (pathParam.matches("^/\\w:.*")) {
      pathParam = pathParam.substring(1);
    }
    Path path = Paths.get(pathParam);
    if (!isReadable(path)) {
      log.warn("File '{}' not found.", pathParam);
      return new ResponseEntity<>(NOT_FOUND);
    }
    Resource logResource = new PathResource(path);
    response.setHeader(CONTENT_DISPOSITION, format("attachment; filename=\"%s\"", path.getFileName().toString()));
    return new ResponseEntity<>(logResource, OK);
  }

}