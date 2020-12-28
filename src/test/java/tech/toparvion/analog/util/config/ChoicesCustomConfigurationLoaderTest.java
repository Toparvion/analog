package tech.toparvion.analog.util.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

class ChoicesCustomConfigurationLoaderTest {
  private static final String PATH_PREFIX = "src/test/resources/autoReload/";

  @Mock
  private ConfigurableEnvironment environment;
  @Mock
  private SpringApplication springApplication;
  @Mock
  private MutablePropertySources mutablePropertySources;
  @InjectMocks
  private ChoicesCustomConfigurationLoader sut;

  @BeforeEach
  void setUp() {
    initMocks(this);

    when(environment.getPropertySources()).thenReturn(mutablePropertySources);
  }

  @AfterEach
  void afterEach() {
    verifyNoInteractions(springApplication);
  }

  @Test
  @DisplayName("Properties was not loaded if path equals to null")
  void choicesCustomLocationIsNull() {
    when(environment.getProperty(anyString())).thenReturn(null);

    sut.postProcessEnvironment(environment, springApplication);

    verify(environment).getProperty("choices-source.location");
    verifyNoMoreInteractions(environment);
    verifyNoInteractions(mutablePropertySources);
  }

  @ParameterizedTest(name = "File path = ''{0}''")
  @ValueSource(strings = {
      "",
      "some/wrong/path",
      PATH_PREFIX,
      PATH_PREFIX + "empty-file.yaml",
      PATH_PREFIX + "file-without-extension",
      PATH_PREFIX + "file-with-incorrect-syntax.yaml",
      PATH_PREFIX + "file-with-wrong-extension.properties"
  })
  @DisplayName("Properties was not loaded if there are some problems with choices custom file")
  void cannotLoadChoicesCustomFile(String pathToChoicesCustomFile) {
    when(environment.getProperty(anyString())).thenReturn(pathToChoicesCustomFile);

    sut.postProcessEnvironment(environment, springApplication);

    verify(environment).getProperty("choices-source.location");
    verifyNoMoreInteractions(environment);
    verifyNoInteractions(mutablePropertySources);
  }

  @Test
  @DisplayName("Only first document was successfully loaded from YAML Multiple Document")
  void yamlMultipleDocument() {
    when(environment.getProperty(anyString())).thenReturn(PATH_PREFIX + "file-with-two-main-blocks.yaml");

    sut.postProcessEnvironment(environment, springApplication);

    verify(environment).getProperty("choices-source.location");
    verify(environment).getPropertySources();
    verify(mutablePropertySources).addFirst(argThat(propertySource -> propertySource.containsProperty("this.is.the.first.document")
        && !propertySource.containsProperty("this.is.the.second.document")));
    verifyNoMoreInteractions(environment, mutablePropertySources);
  }

  @Test
  @DisplayName("Properties was successfully loaded")
  void success() {
    when(environment.getProperty(anyString())).thenReturn(PATH_PREFIX + "correct-file.yaml");

    sut.postProcessEnvironment(environment, springApplication);

    verify(environment).getProperty("choices-source.location");
    verify(environment).getPropertySources();
    verify(mutablePropertySources).addFirst(argThat(propertySource -> propertySource.containsProperty("this.is.the.custom.choices.properties")));
    verifyNoMoreInteractions(environment, mutablePropertySources);
  }
}