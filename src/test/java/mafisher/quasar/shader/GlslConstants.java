package mafisher.quasar.shader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses numeric {@code const float} declarations from a shipped GLSL include. */
public final class GlslConstants {
    private static final Pattern FLOAT_CONSTANT = Pattern.compile(
        "(?m)^\\s*const\\s+float\\s+([A-Za-z_]\\w*)\\s*=\\s*"
            + "([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*;");

    private GlslConstants() {
    }

    public static Map<String, Double> load(String resourceId) {
        String source = ShaderDeclarationScanner.stripComments(
            ShaderTestResources.readShaderSource(resourceId));
        Map<String, Double> values = new LinkedHashMap<>();
        Matcher matcher = FLOAT_CONSTANT.matcher(source);
        while (matcher.find()) {
            values.put(matcher.group(1), Double.parseDouble(matcher.group(2)));
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("No float constants found in " + resourceId);
        }
        return Map.copyOf(values);
    }

    public static double require(Map<String, Double> values, String name) {
        Double value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing GLSL constant: " + name);
        }
        return value;
    }
}
