package ru.ftc.upc.testing.analog.util.timestamp;

import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * An utility service for converting from Java 8 DateTime format strings into conventional regular expressions.
 * <p/>
 * Created by Toparvion on 06.02.2017.
 */
@Service
public class DateFormat2RegexConverter {

  /**
   * Behavioral implementation of the 'presentation' notion from {@link DateTimeFormatter} format rules (see
   * <em>Patterns for Formatting and Parsing</em> section).
   */
  @FunctionalInterface
  interface Presentation {
    String getRegex(int lettersCount);
  }

  /**
   * <b>Year</b>: The count of letters determines the minimum field width below
   * which padding is used. If the count of letters is two, then a
   * {@link DateTimeFormatterBuilder#appendValueReduced reduced} two digit form is
   * used. For printing, this outputs the rightmost two digits. For parsing, this
   * will parse using the base value of 2000, resulting in a year within the range
   * 2000 to 2099 inclusive.
   *
   * @see DateTimeFormatter
   */
  private static final Presentation YEAR = lettersCount -> format("\\d{%d}", lettersCount);

  /**
   * <b>Fraction</b>: Outputs the nano-of-second field as a fraction-of-second.
   * The nano-of-second value has nine digits, thus the count of pattern letters
   * is from 1 to 9. If it is less than 9, then the nano-of-second value is
   * truncated, with only the most significant digits being output.
   *
   * @see DateTimeFormatter
   */
  private static final Presentation FRACTION = lettersCount -> format("\\d{%d}", lettersCount);

  /**
   * <b>Number</b>: If the count of letters is one, then the value is output using
   * the minimum number of digits and without padding. Otherwise, the count of digits
   * is used as the width of the output field, with the value zero-padded as necessary.
   *
   * @see DateTimeFormatter
   */
  private static final Presentation NUMBER = lettersCount -> (lettersCount == 1)
                                                              ? "\\d+"
                                                              : format("\\d{%d}", lettersCount);

  /**
   * <b>Text</b>: The text style is determined based on the number of pattern
   * letters used. Less than 4 pattern letters will use the
   * {@link TextStyle#SHORT short form}. Exactly 4 pattern letters will use the
   * {@link TextStyle#FULL full form}. Exactly 5 pattern letters will use the
   * {@link TextStyle#NARROW narrow form}.
   *
   * @see DateTimeFormatter
   */
  private static final Presentation TEXT = lettersCount -> {
    switch (lettersCount) {
      case 1:
      case 2:
      case 3:
        return format("[\\w\\x20]{%d}", lettersCount);
      case 4:
        return "[\\w\\x20]+";
      case 5:
        return "[\\w\\x20]{1}";
      default:
        return "[\\w\\x20]+";
    }
  };

  /**
   * The Converter's adaptation of subset of format rules defined in {@link DateTimeFormatter}.
   */
  private static final Map<Character, Presentation> dictionary = new HashMap<>();
  static {
    dictionary.put('u', YEAR);      // year
    dictionary.put('y', YEAR);      // year-of-era
    dictionary.put('M', NUMBER);    // month-of-year
    dictionary.put('L', TEXT);      // month-of-year
    dictionary.put('d', NUMBER);    // day-of-month
    dictionary.put('E', TEXT);      // day-of-week

    dictionary.put('a', TEXT);      // am-pm-of-day
    dictionary.put('h', NUMBER);    // clock-hour-of-am-pm (1-12)
    dictionary.put('K', NUMBER);    // hour-of-am-pm (0-11)
    dictionary.put('k', NUMBER);    // clock-hour-of-am-pm (1-24)
    dictionary.put('H', NUMBER);    // hour-of-day (0-23)
    dictionary.put('m', NUMBER);    // minute-of-hour
    dictionary.put('s', NUMBER);    // second-of-minute
    dictionary.put('S', FRACTION);  // fraction-of-second
    dictionary.put('n', NUMBER);    // nano-of-second
  }

  /**
   * Special (meta) characters of Regular Expressions. They should be escaped in final regex to avoid
   * misinterpretation.
   * Taken from http://docs.oracle.com/javase/tutorial/essential/regex/literals.html.
   */
  private static final String REGEX_META_CHARACTERS = "<([{\\^-=$!|]})?*+.>";

  /**
   * Returns Java regex pattern that is able to match the same input string as could be accepted by given
   * format string.<p>
   * The format syntax is a subset of rules specified in {@link DateTimeFormatter}. Namely supported the following
   * symbols only: {@code u,y,M,L,d,E,a,h,K,k,H,m,s,S,n}. Quoting (both single quote and arbitrary text) is also
   * supported.<p>
   * In order to process timestamps'less log lines as fast as possible, the returned pattern contains a prefix
   * denoting searching from the start of a line only ({@code ^} symbol).<p>
   * Because log timestamps are not always located at the very beginning of a line, {@code
   * logTimestampFormat} may contain some additional characters. For instance, if log timestamp are wrapped with
   * square braces, the format string may look like {@code [dd.MM.yy HH:mm:ss.SSS}. The opening square bracket will
   * be put into returning pattern as escaped symbol (to avoid considering it as regex meta char).
   */
  Pattern convertToRegex(String logTimestampFormat) {
    Objects.requireNonNull(logTimestampFormat, "logTimestampFormat must not be null");

    StringBuilder regexBuilder = new StringBuilder(logTimestampFormat.length()*2);
    StringBuilder sameCharsGroup = new StringBuilder(4);
    final int inputLength = logTimestampFormat.length();
    boolean isQuoting = false;

    for(int i=0; i < inputLength; i++) {
      final char curChar = logTimestampFormat.charAt(i);

      if (dictionary.containsKey(curChar) && !isQuoting) {
        sameCharsGroup.append(curChar);
        if (i < (inputLength-1)) {            // haven't reached the end of the input string yet; can look ahead
          char lookAhead = logTimestampFormat.charAt(i+1);
          if (lookAhead != curChar) {         // the group of the same chars is completed so need to release the group
            Presentation presentation = dictionary.get(curChar);
            regexBuilder.append(presentation.getRegex(sameCharsGroup.length()));
            sameCharsGroup.setLength(0);
          } // else (i.e. if next char is equal to curChar) group is not completed yet, just go to the next iteration

        } else {      // have reached the end of the input string; can't look ahead; must release the group as is
          Presentation presentation = dictionary.get(curChar);
          regexBuilder.append(presentation.getRegex(sameCharsGroup.length()));
          sameCharsGroup.setLength(0);        // reset accumulated group of the same chars
        }

      } else if (curChar == '\'') {           // if some kind of quoting is encountered
        if ((i < (inputLength-1)) && (logTimestampFormat.charAt(i+1) == '\'')) {  // if can look ahead and there's a quote
          regexBuilder.append(curChar);                                       // then just add curChar as is
          i++;                                                                // and skip next step
        } else {                              // quote char is not escaped itself so that just toggle quoting mode
          isQuoting = !isQuoting;
        }

      } else {
        String curCharStr = String.valueOf(curChar);
        // don't forget to escape certain characters that can carry special meaning for regex (e.g. dots)
        String escapedChar = REGEX_META_CHARACTERS.contains(curCharStr) ? ("\\" + curCharStr) : curCharStr;
        regexBuilder.append(escapedChar);
      }
    }
    return Pattern.compile(regexBuilder.toString());
  }

}
