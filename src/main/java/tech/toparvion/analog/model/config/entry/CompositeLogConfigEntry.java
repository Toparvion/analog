package tech.toparvion.analog.model.config.entry;

import tech.toparvion.analog.util.PathUtils;

import javax.annotation.Nullable;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Toparvion
 * @since v0.11
 */
@SuppressWarnings("unused")       // setters are used by Spring configuration reading routines
public class CompositeLogConfigEntry extends AbstractLogConfigEntry {
  private List<CompositeInclusion> includes = new ArrayList<>();
  @Nullable
  private String uriName;

  @Override
  public String getId() {
    // compose full id in a form like 'composite://my-app-stack' or 'composite://4FA20BB'
    return String.format("%s%s%s",
        LogType.COMPOSITE.getPrefix(),
        PathUtils.CUSTOM_SCHEMA_SEPARATOR,
        getHKey());
  }

  @Override
  public LogType getType() {
    return LogType.COMPOSITE;
  }

  /**
   * @return human-readable key of composite log: either its {@link #uriName} or {@linkplain #overallHashCode()
   * computed} hash code
   */
  public String getHKey() {
    String idSuffix;
    if (hasText(uriName)) {
      idSuffix = URLEncoder.encode(uriName, UTF_8);
    } else {
      idSuffix = overallHashCode();
    }
    return idSuffix;
  }

  /**
   * Detects if current entry matches given string either by explicit {@link #uriName} or by implicit hashCode. First
   * checks for {@code uriName} matching, then (in case of previous mismatch) checks for matching by hash code.<br/>
   * Both comparisons are case <em>in</em>sensitive.
   * @param idOrCode either user-defined id (e.g. {@code my-composite-log}) or auto-computed code
   *                 (e.g. {@code 35D0F789})
   * @return {@code true} if this entry is identified with given string
   */
  public boolean matches(String idOrCode) {
    if (hasText(uriName)) {
      var decodedIdOrCode = URLDecoder.decode(idOrCode, UTF_8);
      if (uriName.equalsIgnoreCase(decodedIdOrCode)) {
        return true;
      }
    }
    return overallHashCode().equalsIgnoreCase(idOrCode);
  }

  public List<CompositeInclusion> getIncludes() {
    return includes;
  }

  public void setIncludes(List<CompositeInclusion> includes) {
    this.includes = includes;
  }

  @Nullable
  public String getUriName() {
    return uriName;
  }

  public void setUriName(@Nullable String uriName) {
    this.uriName = uriName;
  }

  private String overallHashCode() {
    int inclusionsTotalHashcode = includes.stream()
        .sorted(comparing(CompositeInclusion::toString)) // force order to guarantee stable hash value over invocations
        .mapToInt(CompositeInclusion::hashCode)
        .sum();
    return Integer.toHexString(inclusionsTotalHashcode).toUpperCase();
  }

  @Override
  public String toString() {
    return "CompositeLogConfigEntry{" +
        "includes=" + includes.stream().map(CompositeInclusion::toString).collect(joining(", ")) +
        ", uriName='" + uriName + '\'' +
        ", type='" + getType() + '\'' +
        ", id='" + getId() + '\'' +
        "} " + super.toString();
  }
}
