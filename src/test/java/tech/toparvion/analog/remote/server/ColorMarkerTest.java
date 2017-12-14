package tech.toparvion.analog.remote.server;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.jupiter.api.Test;
import org.simmetrics.MultisetMetric;
import org.simmetrics.StringMetric;
import org.simmetrics.builders.StringMetricBuilder;
import org.simmetrics.metrics.*;
import org.simmetrics.simplifiers.Simplifiers;
import org.simmetrics.tokenizers.Tokenizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Comparator.comparingDouble;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Toparvion
 * @since v0.7
 */
class ColorMarkerTest {
  private static final Logger log = LoggerFactory.getLogger(ColorMarkerTest.class);

  @Test
  void basic() throws IOException, URISyntaxException {
    String refPoint = ColorPicker.REF_POINT;
    List<String> paths = loadLines("test-paths.txt");
    String nodes[] = {"angara", "berkut", "dnepr", "limpopo", "lena", "enisey", "amur", "river"};

    StringMetric singleMetrics[] = {
        new Levenshtein(),
        new DamerauLevenshtein(),
        new Jaro(),
        new JaroWinkler(),
        new NeedlemanWunch(),
        new SmithWaterman(),
        new SmithWatermanGotoh(),
        new LongestCommonSubsequence(),
        new LongestCommonSubstring()
    };
    List<MultisetMetric<String>> multisetMetrics = Arrays.asList(
        new CosineSimilarity<String>(),
        new BlockDistance<String>(),
        new EuclideanDistance<String>(),
        new SimonWhite<String>(),
        new GeneralizedJaccard<>(),
        new GeneralizedOverlapCoefficient<>()
    );

    Random random = new Random();
    Map<String, SummaryStatistics> results = new HashMap<>(20);
    StringBuilder sb = new StringBuilder("Comparison results for strings:\n")
        .append("reference point: ").append(refPoint);
    for (String path : paths) {
      String node = nodes[random.nextInt(nodes.length)];
      String pseudoFullPath = String.format("%s_%s", node, path);
      sb.append("\npath: ").append(pseudoFullPath).append('\n');
      // SINGLE metrics
      for (StringMetric metric : singleMetrics) {
        StringMetric built = StringMetricBuilder
            .with(metric)
            .simplify(Simplifiers.toLowerCase())
            .simplify(Simplifiers.replaceAll("\\\\", "/"))
            .build();
        float similarity = built.compare(refPoint, pseudoFullPath);
        String metricName = metric.getClass().getSimpleName();
        sb.append("SINGLE metric: ").append(similarity).append(" - ").append(metricName).append('\n');
        results.computeIfAbsent(metricName, key -> new SummaryStatistics()).addValue((double) similarity);
      }
      // MULTISET metrics
      for (MultisetMetric<String> metric : multisetMetrics) {
        StringMetric built = StringMetricBuilder
            .with(metric)
            .simplify(Simplifiers.toLowerCase())
            .simplify(Simplifiers.replaceAll("\\\\", "/"))
            .tokenize(Tokenizers.pattern("/"))
            .build();
        float similarity = built.compare(refPoint, pseudoFullPath);
        String metricName = metric.getClass().getSimpleName();
        sb.append("MULTISET metric: ").append(similarity).append(" - ")
            .append(metricName).append('\n');
        results.computeIfAbsent(metricName, key -> new SummaryStatistics()).addValue((double) similarity);
      }
    }
    log.info("{}", sb.toString());

    StringBuilder stats4Log = new StringBuilder("Stats:\n");
    StringBuilder stats4Csv = new StringBuilder();
    results.entrySet().stream()
        .sorted(comparingDouble(entry -> entry.getValue().getStandardDeviation()))
        .forEach(entry -> {
          SummaryStatistics stat = entry.getValue();
          stats4Log.append(String.format("%s (%d samples): stdDev: %.7f, avg: %.7f\n", entry.getKey(), stat.getN(),
              stat.getStandardDeviation(), stat.getMean()));
          stats4Csv.append(String.format("%s;%f;%f\n", entry.getKey(), stat.getMean(), stat.getStandardDeviation()));
        }
    );
    log.info("{}", stats4Log.toString());

    Path resultDir = Paths.get("build/metric-test");
    Files.createDirectories(resultDir);
    Files.write(resultDir.resolve("stat.csv"), stats4Csv.toString().getBytes(), CREATE, TRUNCATE_EXISTING);
  }

  private List<String> loadLines(String fileName) throws URISyntaxException, IOException {
    URL url = Thread.currentThread().getContextClassLoader().getResource(fileName);
    assertNotNull(url);
    Path samplePath = Paths.get(url.toURI());
    assertTrue(Files.exists(samplePath, NOFOLLOW_LINKS));
    return Files.readAllLines(samplePath, StandardCharsets.UTF_8);
  }
}