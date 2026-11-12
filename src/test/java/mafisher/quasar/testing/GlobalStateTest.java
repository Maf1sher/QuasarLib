package mafisher.quasar.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Marks tests that mutate process-wide state such as registries, config, or client impulses.
 * The shared lock keeps them safe when JUnit parallel execution is enabled.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ResourceLock("quasar-global-state")
public @interface GlobalStateTest {
}
