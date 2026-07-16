package io.github.yamlmapper.transform.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

import static io.github.yamlmapper.config.TypeConstants.DEFAULT_TRUNCATE_LENGTH;
import static io.github.yamlmapper.transform.TransformParams.PARAM_MAX_LENGTH;
import static io.github.yamlmapper.transform.TransformParams.PARAM_SUFFIX;

/**
 * Truncates a string to a maximum length.
 *
 * <p>Behavior:
 * <ul>
 *   <li>String longer than maxLength is truncated</li>
 *   <li>String shorter than maxLength is unchanged</li>
 *   <li>Non-string values are returned unchanged</li>
 * </ul>
 *
 * <p>Required parameter {@code maxLength}:
 * <pre>{@code
 * description:
 *   type: string
 *   source: [description]
 *   transform: truncate
 *   transformParams:
 *     maxLength: 500
 * }</pre>
 *
 * <p>Optional parameter {@code suffix} (default: none):
 * <pre>{@code
 * description:
 *   type: string
 *   source: [description]
 *   transform: truncate
 *   transformParams:
 *     maxLength: 500
 *     suffix: "..."
 * }</pre>
 */
public class TruncateTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode node, TransformContext context) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return node;
    }

    int maxLength = context.getParamAsInt(PARAM_MAX_LENGTH, DEFAULT_TRUNCATE_LENGTH);
    String suffix = context.getParam(PARAM_SUFFIX, "");

    String text = node.asText();
    if (text.length() <= maxLength) {
      return node;
    }

    // Adjust maxLength to account for suffix
    int truncateAt = maxLength - suffix.length();
    if (truncateAt < 0) {
      truncateAt = 0;
    }

    String truncated = text.substring(0, truncateAt) + suffix;
    return new TextNode(truncated);
  }

  @Override
  public String getName() {
    return "truncate";
  }
}
