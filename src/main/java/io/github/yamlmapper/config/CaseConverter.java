package io.github.yamlmapper.config;

/**
 * Utility class for converting between camelCase and snake_case naming conventions.
 *
 * <p>This is commonly needed when working with Protobuf (which uses snake_case)
 * and Java/JSON (which typically use camelCase).
 *
 * <p>Examples:
 * <pre>{@code
 * CaseConverter.camelToSnake("visitorId")    // "visitor_id"
 * CaseConverter.camelToSnake("userInfo")     // "user_info"
 * CaseConverter.snakeToCamel("visitor_id")   // "visitorId"
 * CaseConverter.snakeToCamel("user_info")    // "userInfo"
 * }</pre>
 *
 * <p>This class is stateless and thread-safe.
 */
public final class CaseConverter {

  private CaseConverter() {
    // Utility class - not instantiable
  }

  /**
   * Converts camelCase to snake_case.
   *
   * <p>Examples:
   * <ul>
   *   <li>"visitorId" → "visitor_id"</li>
   *   <li>"userInfo" → "user_info"</li>
   *   <li>"HTMLParser" → "h_t_m_l_parser"</li>
   *   <li>"already_snake" → "already_snake"</li>
   * </ul>
   *
   * @param camelCase the camelCase string
   * @return the snake_case equivalent
   */
  public static String camelToSnake(String camelCase) {
    if (camelCase == null || camelCase.isEmpty()) {
      return camelCase;
    }

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < camelCase.length(); i++) {
      char c = camelCase.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          result.append('_');
        }
        result.append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Converts snake_case to camelCase.
   *
   * <p>Examples:
   * <ul>
   *   <li>"visitor_id" → "visitorId"</li>
   *   <li>"user_info" → "userInfo"</li>
   *   <li>"already_camel" → "alreadyCamel"</li>
   *   <li>"alreadyCamel" → "alreadyCamel"</li>
   * </ul>
   *
   * @param snakeCase the snake_case string
   * @return the camelCase equivalent
   */
  public static String snakeToCamel(String snakeCase) {
    if (snakeCase == null || snakeCase.isEmpty()) {
      return snakeCase;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;

    for (int i = 0; i < snakeCase.length(); i++) {
      char c = snakeCase.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
