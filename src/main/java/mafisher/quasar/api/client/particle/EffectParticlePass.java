package mafisher.quasar.api.client.particle;

import java.util.Objects;

/**
 * One stateless particle field declared by a
 * {@link ParticleVisualEffectRenderer}.
 *
 * @param id stable pass ID, unique within one render context
 * @param field immutable particle field
 */
public record EffectParticlePass(String id, EffectParticleField field) {
    public EffectParticlePass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(field, "field");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Particle pass ID must not be blank");
        }
    }
}
