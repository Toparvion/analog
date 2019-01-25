package tech.toparvion.analog.model.config.adapters;

/**
 * @author Toparvion
 * @since 0.11
 */
public class FileAdapterParams extends GeneralAdapterParams {
  private String detectionResponse;

  public String getDetectionResponse() {
    return detectionResponse;
  }

  public void setDetectionResponse(String detectionResponse) {
    this.detectionResponse = detectionResponse;
  }

  @Override
  public String toString() {
    return "FileAdapterParams{" +
            "detectionResponse='" + detectionResponse + '\'' +
            "} " + super.toString();
  }
}
