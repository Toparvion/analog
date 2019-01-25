package tech.toparvion.analog.model.config.adapters;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Toparvion
 * @since 0.11
 */
public class FileAdapterParamSection {
  private String detectionRequest;
  private Map<String, FileAdapterParams> tailImplementations = new LinkedHashMap<>();

  public String getDetectionRequest() {
    return detectionRequest;
  }

  public void setDetectionRequest(String detectionRequest) {
    this.detectionRequest = detectionRequest;
  }

  public Map<String, FileAdapterParams> getTailImplementations() {
    return tailImplementations;
  }

  public void setTailImplementations(Map<String, FileAdapterParams> tailImplementations) {
    this.tailImplementations = tailImplementations;
  }

  @Override
  public String toString() {
    return "FileAdapterParamSection{" +
            "detectionRequest='" + detectionRequest + '\'' +
            ", tailImplementations=" + tailImplementations +
            '}';
  }
}
