package tech.toparvion.analog.remote.agent.origin.adapt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Toparvion
 * @since 0.11
 */
public abstract class AbstractOriginAdapter implements OriginAdapter {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * Executes given command as a command on the underlying OS and reads all the output (including stdError if necessary).
   * @return the whole output that the program returns in reply to command invocation
   * @throws Exception when the program is absent or cannot be accessed by AnaLog
   * @param checkingCommand fully composed command to execute, for example
   * {@code sudo docker version --format '{{ .Client.Version}}'}
   */
  String obtainIdfString(String checkingCommand) throws Exception {
    // first check whether executable is present and try to run it
    Process process;
    try {
      process = Runtime.getRuntime().exec(checkingCommand);

    } catch (IOException e) {
      // for the source of message see java.lang.ProcessBuilder.start(java.lang.ProcessBuilder.Redirect[])
      if (e.getMessage().startsWith("Cannot run program")) {
        String explanationMessage = format("Failed to execute '%s' command on this server. Please check if it is correctly" +
                " spelled and accessible for AnaLog. If command contains non-absolute path to executable, please make" +
                " sure it can be either resolved from current directory or its path is contained in PATH environment" +
                " variable. Original error message: %s", checkingCommand, e.getMessage());
        throw new IllegalStateException(explanationMessage, e);
      } else {
        throw e;
      }
    }
    // then read the whole output it has printed
    String idfString = readAllOutput(process.getInputStream());

    if (!idfString.isEmpty()) {
      log.debug("Obtained idf line from STANDARD output:\n{}", idfString);

    } else {
      // the command might reply in error stream, e.g. on Solaris OS, so let's check if error output contains any data
      idfString = readAllOutput(process.getErrorStream());
      log.debug("Obtained idf line from ERROR output:\n{}", idfString);
    }
    log.trace("Waiting for command to finish...");
    process.waitFor();
    log.debug("Command '{}' has finished. Going on...", checkingCommand);
    return idfString;
  }

  private String readAllOutput(InputStream inputStream) throws IOException {
    StringBuilder idfStringBuilder = new StringBuilder();
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        if (idfStringBuilder.length() > 0) {
          idfStringBuilder.append('\n');
        }
        idfStringBuilder.append(line);
      }
    }
    return idfStringBuilder.toString();
  }


}
