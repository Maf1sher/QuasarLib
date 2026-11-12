package mafisher.quasar.shader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the declarations a shader JSON must bind, without executing GLSL. */
final class ShaderDeclarationScanner {
    private static final Pattern UNIFORM = Pattern.compile(
        "(?m)^\\s*uniform\\s+(?:(?:highp|mediump|lowp)\\s+)?([A-Za-z_]\\w*)\\s+"
            + "([A-Za-z_]\\w*)\\s*(\\[\\s*\\d+\\s*\\])?\\s*;");
    private static final Pattern INPUT = Pattern.compile(
        "(?m)^\\s*(?:(flat)\\s+)?(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+"
            + "([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*(\\[\\s*\\d+\\s*\\])?\\s*;");
    private static final Pattern OUTPUT = Pattern.compile(
        "(?m)^\\s*(?:(flat)\\s+)?(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+"
            + "([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*(\\[\\s*\\d+\\s*\\])?\\s*;");

    private ShaderDeclarationScanner() {
    }

    static Declarations scan(String source) {
        String clean = stripComments(source);
        List<Declaration> uniforms = new ArrayList<>();
        List<Declaration> samplers = new ArrayList<>();
        Matcher uniform = UNIFORM.matcher(clean);
        while (uniform.find()) {
            Declaration declaration = new Declaration(
                uniform.group(1), uniform.group(2), false, uniform.group(3) != null);
            uniforms.add(declaration);
            if (isSampler(declaration.type())) {
                samplers.add(declaration);
            }
        }
        return new Declarations(
            List.copyOf(uniforms),
            List.copyOf(samplers),
            declarations(INPUT, clean),
            declarations(OUTPUT, clean)
        );
    }

    private static List<Declaration> declarations(Pattern pattern, String source) {
        List<Declaration> declarations = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            declarations.add(new Declaration(
                matcher.group(2), matcher.group(3), matcher.group(1) != null,
                matcher.group(4) != null));
        }
        return List.copyOf(declarations);
    }

    private static boolean isSampler(String type) {
        return type.startsWith("sampler") || type.startsWith("isampler")
            || type.startsWith("usampler");
    }

    static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    result.append(current);
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                } else if (current == '\n') {
                    result.append(current);
                }
                continue;
            }
            if (current == '/' && next == '/') {
                lineComment = true;
                index++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    record Declaration(String type, String name, boolean flat, boolean array) {
    }

    record Declarations(List<Declaration> uniforms, List<Declaration> samplers,
        List<Declaration> inputs, List<Declaration> outputs) {
        static Set<String> names(List<Declaration> declarations) {
            Set<String> names = new LinkedHashSet<>();
            for (Declaration declaration : declarations) {
                names.add(declaration.name());
            }
            return names;
        }
    }
}
