package tech.toparvion.analog.remote.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Toparvion
 * @since v0.7
 */
class ColorPickerTest {
  private static String nodes[] = {"angara", "berkut", "dnepr", "limpopo", "lena", "enisey", "amur", "tunguska", "river"};

  @Test
  void getColor() throws URISyntaxException, IOException {
    URL url = Thread.currentThread().getContextClassLoader().getResource("test-paths.txt");
    assertNotNull(url);
    Path samplePath = Paths.get(url.toURI());
    assertTrue(Files.exists(samplePath, NOFOLLOW_LINKS));

    List<String> samples = Files.readAllLines(samplePath, StandardCharsets.UTF_8);
    Random random = new Random();

    ColorPicker colorPicker = new ColorPicker();
    List<String> colors = new ArrayList<>(samples.size());
    for (String sample : samples) {
      String node = nodes[random.nextInt(nodes.length)];
      String color = colorPicker.pickColor(sample, node, "345gh3fc");
      colors.add(color);
    }

    Path resultDir = Paths.get("build/metric-test");
    Files.createDirectories(resultDir);
    Files.write(resultDir.resolve("picked-colors.csv"), colors, CREATE, TRUNCATE_EXISTING);
  }
}