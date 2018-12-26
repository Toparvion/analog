package tech.toparvion.analog.model.config;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.toparvion.analog.model.config.entry.CompositeLogConfigEntry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Toparvion
 * @since v0.11
 */
@SpringBootTest//(classes = ChoiceProperties.class)
class ChoicePropertiesTest {
  private static final Logger log = LoggerFactory.getLogger(ChoicePropertiesTest.class);

  @Autowired
  ChoiceProperties choiceProperties;

  @Test
  void getCompositeChoices() {
    List<ChoiceGroup> groups = choiceProperties.getChoices();
    assertThat(groups).isNotEmpty();
    List<CompositeLogConfigEntry> compositeLogs = groups.get(0).getCompositeLogs();
    assertThat(compositeLogs).isNotEmpty();
    log.info("hashCode: {}", compositeLogs.get(0).getHKey());

  }
}