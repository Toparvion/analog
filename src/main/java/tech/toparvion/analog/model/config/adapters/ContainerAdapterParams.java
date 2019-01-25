package tech.toparvion.analog.model.config.adapters;

/**
 * @author Toparvion
 * @since 0.11
 */
public class ContainerAdapterParams extends GeneralAdapterParams {
  private String versionCommand;

  public String getVersionCommand() {
    return versionCommand;
  }

  public void setVersionCommand(String versionCommand) {
    this.versionCommand = versionCommand;
  }

  @Override
  public String toString() {
    return "ContainerAdapterParams{" +
            "versionCommand='" + versionCommand + '\'' +
            "} " + super.toString();
  }
}
