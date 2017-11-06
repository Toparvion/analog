package tech.toparvion.analog.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Toparvion
 */
public class UtilTest {

  @Test
  public void fileNameIsExtractedFromAnyPath() throws Exception {
    String path, actualFileName;
    String expectedFileName = "access.log";

    path = "/pub/home/user/logs/access.log";
    actualFileName = Util.extractFileName(path);
    assertEquals(expectedFileName, actualFileName);

    path = "C:\\Users\\user\\logs\\access.log";
    actualFileName = Util.extractFileName(path);
    assertEquals(expectedFileName, actualFileName);

    path = "access.log";
    actualFileName = Util.extractFileName(path);
    assertEquals(expectedFileName, actualFileName);
  }

}