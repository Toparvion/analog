package tech.toparvion.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.config.RecordLevelsProperties;

import java.util.List;
import java.util.Optional;

import static java.lang.Character.toLowerCase;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * A component responsible for determining which level a record belongs to. Scans first line of every given record in
 * order to find known level entry. This is the third and the most complicated implementation of record level
 * detection logic aimed at both accuracy and performance at the same time.
 * @author Toparvion
 * @since v0.8.1
 */
@Service
public class RecordLevelDetector {
  private static final Logger log = LoggerFactory.getLogger(RecordLevelDetector.class);

  /**
   * Matrix of characters of all known record levels. Allows access by row and columns indexes. Built upon distinct
   * level values.
   */
  private final LevelMatrix levelMatrix;
  /**
   * Immutable list of distinct levels. Used as an ordered storage of levels to extract levels after successful
   * detection thus avoiding excess memory allocations (in comparison with levelMatrix)
   */
  private final List<String> distinctLevels;

  @Autowired
  public RecordLevelDetector(RecordLevelsProperties recordLevelsProperties,
                             @Value("${recordLevelsDetector.caseSensitive:true}") boolean isCaseSensitive) {
    List<String> knownLevels = recordLevelsProperties.getRecordLevels();
    this.distinctLevels = knownLevels.stream()
        .distinct()
        .collect(toList());
    if (distinctLevels.size() != knownLevels.size()) {
      log.warn("Proposed list of record levels contains duplicates and therefore has been reduced to {}", distinctLevels);
    } else {
      log.info("Record level detector has been initialized as caseSensitive={} and with known levels: {}",
          isCaseSensitive, distinctLevels);
    }
    levelMatrix = new LevelMatrix(distinctLevels, isCaseSensitive);
  }

  /**
   * Detects the level of given log record basing on {@linkplain #distinctLevels known levels}. Reads only the very
   * first line of the record from left to right and stops when either EOL/EOR (end of record) encountered or when the
   * full match is found. The full match is defined as (1) lengths equality between level and a word within the
   * record, (2) precise character matching.
   * @param record record to detect level of
   * @return either one of levels given early to {@linkplain #RecordLevelDetector(RecordLevelsProperties, boolean)
   * constructor} or {@linkplain Optional#empty() empty optional} if no level was detected
   */
  public Optional<String> detectLevel(String record) {
    for (int curPos = 0; curPos < record.length(); curPos++) {
      char curChar = record.charAt(curPos);
      if (curChar == '\n') {
        return Optional.empty();
      }
      char prevChar = (curPos > 0)
          ? record.charAt(curPos-1)
          : 0;          // we treat prevChar as 0 at the record's start in order to detect it as ordinary word start
      // word left boundary is where a non-alphanumerical character is followed by alphanumerical one
      boolean isWordLeftBoundary = !isAlphaNum(prevChar) && isAlphaNum(curChar);
      if (!isWordLeftBoundary) {
        continue;       // there is no sense in analyzing words not from the beginning
      }

      int wordAnalysisResult = analyzeWord(record, curPos);
      if (wordAnalysisResult >= 0) {
        //noinspection UnnecessaryLocalVariable
        int matchedLevelIndex = wordAnalysisResult;
        return Optional.of(distinctLevels.get(matchedLevelIndex));
      }
      curPos += (Math.abs(wordAnalysisResult)-1);   // -1 to avoid one excess increment on transition to next loop step
    }

    return Optional.empty();
  }

  /**
   * Checks a word within {@code record} starting at position {@code startPos} for the full matching with any known
   * record level. Result of a method call may be of two kinds. If negative, the result means that no match was found
   * and it is just length of the analyzed word (for main loop to skip the word entirely). Otherwise the result is
   * index of matched record level in {@link #levelMatrix}.
   *
   * @param record the whole record containing a word to analyze
   * @param startPos word first letter's position
   * @return word length ({@code <0}) or matched record level's index ({@code >=0)}
   */
  private int analyzeWord(String record, int startPos) {
    int curPos = startPos;
    char curChar = record.charAt(startPos);
    int wordPos = 0;
    boolean[] matchingLevels = new boolean[levelMatrix.getLevelsCount()];
    int firstMatchingLevelIdx;
    do {
      matchingLevels = levelMatrix.markMatchingLevels(curChar, wordPos, matchingLevels);
      firstMatchingLevelIdx = findFirstMatchingLevelIdx(matchingLevels);
      if (firstMatchingLevelIdx < 0) {
        // skip current word entirely
        while (curPos < record.length() && isAlphaNum(record.charAt(curPos++))) {
          wordPos++;
        }
        return -wordPos;      // return word position as negative to denote the mismatch (see method javadoc)
      }
      curPos++;
      curChar = curPos < record.length()
          ? record.charAt(curPos)
          : 0;      // we treat the end of the record as 0 in order to detect it as ordinary word stop
      wordPos++;
    } while (isAlphaNum(curChar));

    return levelMatrix.isLevelMatchesEntirely(firstMatchingLevelIdx, wordPos)
        ? firstMatchingLevelIdx
        : -1;
  }

  private int findFirstMatchingLevelIdx(boolean[] matchingLevels) {
    int firstMatchingLevelIdx = -1;
    for (int i = 0; i < matchingLevels.length; i++) {
      boolean matchingLevel = matchingLevels[i];
      if (matchingLevel) {
        firstMatchingLevelIdx = i;
        break;
      }
    }
    return firstMatchingLevelIdx;
  }

  private static boolean isAlphaNum(char aChar) {
    return (aChar >= 'A' && aChar <= 'Z')
        || (aChar >= 'a' && aChar <= 'z')
        || (aChar >= '0' && aChar <= '9')
        || (aChar == '_');
  }

  private static class LevelMatrix {
    private final char[][] rows;
    private final boolean isCaseSensitive;

    LevelMatrix(List<String> levels, boolean isCaseSensitive) {
      rows = new char[levels.size()][];
      this.isCaseSensitive = isCaseSensitive;
      int i = 0;
      for (String level : levels) {
        rows[i++] = checkAndGetChars(level);
      }
    }

    /**
     * Checks {@code curChar} for the matching with levels at the {@code wordPos} position. The checking is done only
     * for levels which have {@code true} at their indexes at {@code prevMatches}. The exclusion is made for the very
     * first position only ({@code wordPos==0}) as there are no previous matched levels yet.
     * @param curChar current character of the word being analyzed
     * @param wordPos current position within the word being analyzed
     * @param prevMatches the result of previous method call
     * @return {@code prevMatches} where matched levels' indexes are set to {@code true} and other are {@code false}
     */
    boolean[] markMatchingLevels(char curChar, int wordPos, boolean[] prevMatches) {
      for (int i = 0; i < prevMatches.length; i++) {
        boolean prevMatch = prevMatches[i];
        if (wordPos != 0 && !prevMatch)
          continue;
        char[] row = rows[i];
        prevMatches[i] = (wordPos < row.length) && areCharsEqual(row[wordPos], curChar);
      }
      return prevMatches;
    }

    int getLevelsCount() {
      return rows.length;
    }

    boolean isLevelMatchesEntirely(int levelIdx, int wordPos) {
      return (rows[levelIdx].length == wordPos);
    }

    private boolean areCharsEqual(char a, char b) {
      return isCaseSensitive
          ? a == b
          : toLowerCase(a) == toLowerCase(b);
    }

    private char[] checkAndGetChars(String level) throws IllegalArgumentException {
      char[] levelChars = level.toCharArray();
      for (char levelChar : levelChars) {
        if (!isAlphaNum(levelChar)) {
          throw new IllegalArgumentException(format("Unable to initialize record level detector: level '%s' contains " +
              "non-alphanumerical character '%s'. Levels must contain the following characters only: " +
              "'A'-'Z', 'a'-'z', '_', '0'-'9'.", level, levelChar));
        }
      }
      return levelChars;
    }
  }

}
