/**
 * Client micro-particle API.
 *
 * Stateless GPU particle fields seeded by the effect seed, the effect age, and
 * the particle instance index. The engine owns the draw path and the count
 * budget; consumers declare shapes, motion, and appearance.
 *
 * <p>Types in this package are part of the supported consumer contract.
 * Implementation packages outside {@code mafisher.quasar.api} are internal.</p>
 */
@NullMarked
package mafisher.quasar.api.client.particle;

import org.jspecify.annotations.NullMarked;
