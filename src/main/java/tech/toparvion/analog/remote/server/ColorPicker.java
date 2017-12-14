package tech.toparvion.analog.remote.server;

import org.simmetrics.StringMetric;
import org.simmetrics.metrics.JaroWinkler;
import org.simmetrics.simplifiers.Simplifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static org.simmetrics.builders.StringMetricBuilder.with;

/**
 * A service responsible for choosing colors for highlighting of composite records.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class ColorPicker {
  private static final Logger log = LoggerFactory.getLogger(ColorPicker.class);
  private static final StringMetric SIMILARITY_ALGORITHM = new JaroWinkler();
  /*private - for unit test*/ static final String REF_POINT =
      "limpopo_/pub/home/upc/applications/upc2mt/src/projects/core/log01/core-all.log";
  //      "iQfKYHc16_Z5RB)F:2j/rc/et0FUaEGFhq8J_iK/L.WjIexO/RX7nfU'K/VpJZMkh)unU";
  private static final float STD_DEVIATION = 0.071377f;
  private static final float MEAN = 0.585903f;
  private static final int RING_SIZE = Color.values().length;

  private final Map<String/*:uid*/, Map<String/*:pseudoFullPath*/, Color/*:color*/>> colorsMap = new ConcurrentHashMap<>();

  public String pickColor(String path, String node, String uid) {
    String pseudoFulPath = format("%s_%s", node, path);

    // first let's check if there is a color assigned to given path within given uid
    Map<String, Color> uidColors = colorsMap.computeIfAbsent(uid, s -> new ConcurrentHashMap<>());
    Color cachedColor = uidColors.get(pseudoFulPath);
    if (cachedColor != null) {
      // color has been already computed; return it immediately
      return cachedColor.name().toLowerCase();
    }

    // no color found for given path within given uid so let's compute it
    Color computedColor = computeColor(pseudoFulPath);
    // and correct it if necessary in order to avoid same color usage withing single uid
    Color correctedName = correctColorWithinUid(uid, uidColors.values(), computedColor);
    // then store the computed color (either corrected or not) in the cache to accelerate subsequent queries
    uidColors.put(pseudoFulPath, correctedName);
    return correctedName.name().toLowerCase();
  }

  /**
   * Chooses a color basing on computations of given path's string distance from {@link #REF_POINT}. May return the
   * same color for different paths therefore the result may require some sort of correction if uniqueness is
   * important.
   * @param pseudoFulPath log file path (including node name) for which the color must be computed
   * @return color corresponding to given path and matching the following conditions:<ul>
   *   <li>for the same path the same color is always returned;</li>
   *   <li>no similarity between colors is assumed in relation to paths similarity;</li>
   *   <li>distribution of colors is normal, not uniform as they are chosen basing on normally distributed (random)
   *   distance function value.</li>
   * </ul>
   */
  private Color computeColor(String pseudoFulPath) {
    float similarity = with(SIMILARITY_ALGORITHM)
        .simplify(Simplifiers.toLowerCase())
        .simplify(Simplifiers.replaceAll("\\\\", "/"))
        .build()
        .compare(pseudoFulPath, REF_POINT);
    float rawDistance = 1.0f - similarity;
    float normalizedDistance = normalizeDistance(rawDistance);
    int rawIndex = Math.round((float)RING_SIZE * normalizedDistance);
    int normalizedIndex = normalizeIndex(rawIndex);   // rawIndex % RING_SIZE;
    log.debug(format("rawDistance: %.7f normDistance: %.7f rawIndex: %2d normIndex: %2d path: %s",
        rawDistance, normalizedDistance, rawIndex, normalizedIndex, pseudoFulPath));
    Color computedColor = Color.values()[normalizedIndex];
    log.info("Color '{}' has been computed for path '{}'.", computedColor, pseudoFulPath);
    return computedColor;
  }

  /**
   * Compares given {@code computedColor} with other colors among {@code uidColors} (for given {@code uid}) and in
   * case of finding the same one chooses another color. Does not change {@code uidColors}.
   * @param uid {@code uid} value to write to log
   * @param occupiedColors colors that already occupied within given {@code uid}
   * @param computedColor color that was initially {@link #computeColor(String) computed}
   * @return a color either the same as {@code computedColor} or corrected one
   */
  private Color correctColorWithinUid(String uid, Collection<Color> occupiedColors, Color computedColor) {
    // check for uniqueness among other colors for this uid
    if (!occupiedColors.contains(computedColor)) {
      log.debug("Color '{}' hasn't yet been used for uid '{}'. No correction is needed.", computedColor, uid);
      return computedColor;
    }
    // if the color is already associated with this uid...
    int startIndex = Color.indexOf(computedColor);
    log.debug("Color '{}' is already used for uid '{}'. Starting to search for other color from " +
            "index next to {}...", computedColor, uid, startIndex);
    Color correctedName = computedColor;
    int i;
    for (i = 1/*to start searching from the next index*/; i < RING_SIZE; i++) {
      int nextIndex = (i + startIndex) % RING_SIZE;
      Color nextSuggestedName = Color.values()[nextIndex];
      if (!occupiedColors.contains(nextSuggestedName)) {
        correctedName = nextSuggestedName;
        log.debug("Color '{}' has been chosen as next free one for uid '{}'.", nextSuggestedName, uid);
        break;
      }
    }
    if (i == RING_SIZE) {
      Color randomColor = Color.values()[new Random().nextInt(RING_SIZE)];
      correctedName = randomColor;
      log.warn("No free color name found among {} available names. Chosen '{}' randomly.", RING_SIZE, randomColor);
    }
    log.info("Computed color '{}' has been corrected to '{}' due to collision on uid '{}'.",
        computedColor, correctedName, uid);
    return correctedName;
  }

  private float normalizeDistance(float distance) {
    return (distance - MEAN) / (4f * STD_DEVIATION) + 0.5f;
  }

  private int normalizeIndex(int rawIndex) {
    return BigInteger.valueOf(rawIndex)
              .mod(
           BigInteger.valueOf(RING_SIZE))
              .intValue();
  }

  @SuppressWarnings("unused")   // all the colors are also declared in 'asset/composite-record.styl'
  private enum Color {
    BLUE,
    GREEN,
    ORANGE,
    BROWN,
    VIOLET,
    ROSE;

    static int indexOf(Color entry) {
      Color[] values = values();
      for (int i = 0; i < values.length; i++) {
        Color color = values[i];
        if (color == entry) {
          return i;
        }
      }
      throw new IllegalArgumentException("No such color name: " + entry.name());
    }
  }


}
