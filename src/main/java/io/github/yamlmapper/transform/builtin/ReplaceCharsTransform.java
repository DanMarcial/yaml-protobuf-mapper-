package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Replaces characters or substrings in a string.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code from} - The character/string to replace</li>
 *   <li>{@code to} - The replacement character/string</li>
 *   <li>{@code regex} - Whether 'from' is a regex (default: false)</li>
 * </ul>
 *
 * <p>Example YAML:
 * <pre>{@code
 * languageCode:
 *   type: string
 *   source: [locale]
 *   transform: replaceChars
 *   transformParams:
 *     from: "_"
 *     to: "-"
 * }</pre>
 *
 * <p>Input: "en_US"
 * <p>Output: "en-US"
 */
public class ReplaceCharsTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || input.isMissingNode()) {
      return input;
    }

    if (!input.isTextual()) {
      return input;
    }

    String text = input.asText();
    if (text == null || text.isEmpty()) {
      return input;
    }

    String from = context.getParam("from", "");
    String to = context.getParam("to", "");
    boolean useRegex = context.getParamAsBoolean("regex", false);

    if (from.isEmpty()) {
      return input;
    }

    String result;
    if (useRegex) {
      result = text.replaceAll(from, to);
    } else {
      result = text.replace(from, to);
    }

    return new TextNode(result);
  }

  @Override
  public String getName() {
    return "replaceChars";
  }
}
