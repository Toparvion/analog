package tech.toparvion.analog.service.choice;

import static tech.toparvion.analog.util.PathUtils.extractFileName;

/**
 * @author Toparvion
 */
public class LogChoiceUtils {

  public static String expandTitle(String pureTitle, String purePath, String groupName) {
    String fileName = extractFileName(purePath);
    return pureTitle.replaceAll("(?i)\\$f", fileName)
        .replaceAll("(?i)\\$g", groupName)
        .replaceAll("(^\")|(\"$)", "");
  }
}
