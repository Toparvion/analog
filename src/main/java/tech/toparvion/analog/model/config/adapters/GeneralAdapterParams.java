package tech.toparvion.analog.model.config.adapters;

/**
 * @author Toparvion
 * @since 0.11
 */
public class GeneralAdapterParams {
  private String executable;
  private String followCommand;

  public String getExecutable() {
    return executable;
  }

  public void setExecutable(String executable) {
    this.executable = executable;
  }

  public String getFollowCommand() {
    return followCommand;
  }

  public void setFollowCommand(String followCommand) {
    this.followCommand = followCommand;
  }

  @Override
  public String toString() {
    return "GeneralAdapterParams{" +
            "executable='" + executable + '\'' +
            ", followCommand='" + followCommand + '\'' +
            '}';
  }
}
