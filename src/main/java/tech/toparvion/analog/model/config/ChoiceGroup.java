package tech.toparvion.analog.model.config;

import tech.toparvion.analog.model.config.entry.CompositeLogConfigEntry;
import tech.toparvion.analog.model.config.entry.PlainLogConfigEntry;
import tech.toparvion.analog.model.config.entry.ScanLocations;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class ChoiceGroup {

  private String group                                    = "(non-grouped)";
  private String localPlainLogsBase                       = "";
  private ScanLocations scanLocations                     = null;
  private List<CompositeLogConfigEntry> compositeLogs     = new ArrayList<>();
  private List<PlainLogConfigEntry> plainLogs             = new ArrayList<>();

  public ChoiceGroup() { }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getLocalPlainLogsBase() {
    return localPlainLogsBase;
  }

  public void setLocalPlainLogsBase(String localPlainLogsBase) {
    this.localPlainLogsBase = localPlainLogsBase;
  }

  public ScanLocations getScanLocations() {
    return scanLocations;
  }

  public void setScanLocations(ScanLocations scanLocations) {
    this.scanLocations = scanLocations;
  }

  public List<CompositeLogConfigEntry> getCompositeLogs() {
    return compositeLogs;
  }

  public void setCompositeLogs(List<CompositeLogConfigEntry> compositeLogs) {
    this.compositeLogs = compositeLogs;
  }

  public List<PlainLogConfigEntry> getPlainLogs() {
    return plainLogs;
  }

  public void setPlainLogs(List<PlainLogConfigEntry> plainLogs) {
    this.plainLogs = plainLogs;
  }

  @Override
  public String toString() {
    return "ChoiceGroup{" +
        "group='" + group + '\'' +
        ", plainLogsLocalBase='" + localPlainLogsBase + '\'' +
        ", scanLocations=" + scanLocations +
        ", compositeLogsSize=" + compositeLogs.size() +
        ", plainLogsSize=" + plainLogs.size() +
        '}';
  }
}
