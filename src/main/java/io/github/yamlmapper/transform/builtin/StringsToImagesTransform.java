package io.github.yamlmapper.transform.builtin;

import static io.github.yamlmapper.transform.TransformParams.PARAM_DEFAULT_HEIGHT;
import static io.github.yamlmapper.transform.TransformParams.PARAM_DEFAULT_WIDTH;
import static io.github.yamlmapper.transform.TransformParams.PARAM_URI_FIELD;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yamlmapper.transform.Transform;
import io.github.yamlmapper.transform.TransformContext;

/**
 * Converts an array of string URLs to an array of Image objects.
 *
 * <p>Each string URL becomes an Image object with the URI set.
 * Optional default width/height can be specified.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code uriField} - The field name for URI in output (default: "uri")</li>
 *   <li>{@code defaultWidth} - Default width for images (optional)</li>
 *   <li>{@code defaultHeight} - Default height for images (optional)</li>
 * </ul>
 *
 * <p>Example YAML:
 * <pre>{@code
 * images:
 *   type: array
 *   itemType: Image
 *   source: [image_gallery]
 *   transform: stringsToImages
 * }</pre>
 *
 * <p>Input: ["https://cdn.com/img1.jpg", "https://cdn.com/img2.jpg"]
 * <p>Output: [{"uri": "https://cdn.com/img1.jpg"}, {"uri": "https://cdn.com/img2.jpg"}]
 */
public class StringsToImagesTransform implements Transform {

  @Override
  public JsonNode apply(JsonNode input, TransformContext context) {
    if (input == null || input.isNull() || input.isMissingNode()) {
      return context.getObjectMapper().createArrayNode();
    }

    // If it's already an array of objects, return as-is
    if (input.isArray() && input.size() > 0 && input.get(0).isObject()) {
      return input;
    }

    // If it's a single string, wrap in array first
    ArrayNode inputArray;
    if (input.isTextual()) {
      inputArray = context.getObjectMapper().createArrayNode();
      inputArray.add(input);
    } else if (input.isArray()) {
      inputArray = (ArrayNode) input;
    } else {
      return context.getObjectMapper().createArrayNode();
    }

    String uriField = context.getParam(PARAM_URI_FIELD, "uri");
    int defaultWidth = context.getParamAsInt(PARAM_DEFAULT_WIDTH, 0);
    int defaultHeight = context.getParamAsInt(PARAM_DEFAULT_HEIGHT, 0);

    ArrayNode result = context.getObjectMapper().createArrayNode();

    for (JsonNode element : inputArray) {
      if (element.isTextual()) {
        String uri = element.asText();
        if (uri != null && !uri.isBlank()) {
          ObjectNode imageObj = context.getObjectMapper().createObjectNode();
          imageObj.put(uriField, uri);

          if (defaultWidth > 0) {
            imageObj.put("width", defaultWidth);
          }
          if (defaultHeight > 0) {
            imageObj.put("height", defaultHeight);
          }

          result.add(imageObj);
        }
      } else if (element.isObject()) {
        // Already an object, pass through
        result.add(element);
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "stringsToImages";
  }
}
