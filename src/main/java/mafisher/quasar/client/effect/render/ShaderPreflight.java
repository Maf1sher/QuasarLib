package mafisher.quasar.client.effect.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates every registered {@link EffectShader} against the current resources
 * before the client starts a resource reload. A failed preflight leaves the
 * loaded programs untouched.
 */
@ApiStatus.Internal
public final class ShaderPreflight {
    private static final int DEFAULT_CHAT_FAILURES = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderPreflight.class);

    private ShaderPreflight() {
    }

    /** Runs the production GLSL and JSON checks for all registered handles. */
    public static Report check(ResourceProvider resources) {
        return check(resources, ShaderProgramValidator::validate);
    }

    static Report check(ResourceProvider resources, Validator validator) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(validator, "validator");
        long start = System.nanoTime();
        List<EffectShader> handles = EffectShaderCatalog.handles();
        List<ShaderFailure> failures = new ArrayList<>();
        for (EffectShader handle : handles) {
            try {
                validator.validate(handle, resources, failures);
            } catch (RuntimeException exception) {
                LOGGER.error("Shader preflight crashed for {}", handle.id(), exception);
                String detail = exception.toString();
                failures.add(new ShaderFailure(handle.id(), "preflight", handle.id().toString(), "",
                    List.of(), ShaderProgramDiagnostics.summary(detail), detail));
            }
        }
        Report report = new Report(handles.size(), failures, System.nanoTime() - start);
        report.failures().forEach(ShaderPreflight::logFailure);
        return report;
    }

    private static void logFailure(ShaderFailure failure) {
        LOGGER.error("Shader preflight failed for {} [{}] {}{}\nimports: {}\n{}",
            failure.programId(), failure.stage(), failure.resource(),
            failure.pack().isEmpty() ? "" : " (" + failure.pack() + ")",
            failure.imports().isEmpty() ? "none" : String.join(", ", failure.imports()),
            failure.detail());
    }

    @FunctionalInterface
    interface Validator {
        void validate(EffectShader shader, ResourceProvider resources, List<ShaderFailure> failures);
    }

    /** Immutable preflight result used by the command and by tests. */
    public record Report(int checked, List<ShaderFailure> failures, long elapsedNanos) {
        public Report {
            failures = List.copyOf(failures);
        }

        public boolean valid() {
            return failures.isEmpty();
        }

        public double elapsedMillis() {
            return elapsedNanos / 1_000_000.0;
        }

        public List<String> chatLines() {
            return chatLines(DEFAULT_CHAT_FAILURES);
        }

        public List<String> chatLines(int maxFailures) {
            if (failures.isEmpty()) {
                return List.of("Validated " + checked + " shader program(s) in "
                    + String.format(Locale.ROOT, "%.1f", elapsedMillis()) + " ms.");
            }
            List<String> lines = new ArrayList<>();
            lines.add("Shader preflight failed for " + failures.size() + " check(s) across "
                + checked + " program(s); nothing was reloaded. The last valid programs remain.");
            int shown = 0;
            for (ShaderFailure failure : failures) {
                if (shown >= Math.max(0, maxFailures)) {
                    break;
                }
                lines.add(format(failure));
                shown++;
            }
            if (failures.size() > shown) {
                lines.add("... " + (failures.size() - shown) + " more check(s); see the log.");
            }
            return List.copyOf(lines);
        }

        private static String format(ShaderFailure failure) {
            StringBuilder line = new StringBuilder(failure.programId() + " [")
                .append(failure.stage()).append("] ").append(failure.resource());
            if (!failure.pack().isEmpty()) {
                line.append(" (").append(failure.pack()).append(')');
            }
            line.append(": ").append(failure.summary());
            if (!failure.imports().isEmpty()) {
                line.append(" | imports: ").append(String.join(", ", failure.imports()));
            }
            return line.toString();
        }
    }
}
