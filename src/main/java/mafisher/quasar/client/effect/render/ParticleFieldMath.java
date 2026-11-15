package mafisher.quasar.client.effect.render;

import java.util.Objects;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import net.minecraft.world.phys.Vec3;

/**
 * Headless mirror of the GPU particle formula. The vertex shader in
 * {@code particle_field.glsl} implements the same streams and closed-form path; tests
 * use this class to fix determinism, shape bounds, and culling bounds.
 *
 * <p>All randomness comes from integer hash streams, so the values are stable across
 * runs and do not depend on time, allocation order, or client state.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class ParticleFieldMath {
    static final double TWO_PI = Math.PI * 2.0;
    private static final double INV_TWO_POWER_32 = 1.0 / 4_294_967_296.0;

    private ParticleFieldMath() {
    }

    static int hash(int seedLow, int seedHigh, int index, int stream) {
        int value = seedLow ^ seedHigh * 0x9E3779B9 ^ index * 0x85EBCA6B ^ stream * 0xC2B2AE35;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    static float random(int seedLow, int seedHigh, int index, int stream) {
        return (float)(Integer.toUnsignedLong(hash(seedLow, seedHigh, index, stream))
            * INV_TWO_POWER_32);
    }

    static Vec3 spawn(EffectParticleShape shape, int seedLow, int seedHigh, int index) {
        return switch (shape) {
            case EffectParticleShape.Point point -> Vec3.ZERO;
            case EffectParticleShape.Sphere sphere -> sphereSpawn(sphere, seedLow, seedHigh, index);
            case EffectParticleShape.Box box -> new Vec3(
                mix(box.min().x, box.max().x, random(seedLow, seedHigh, index, 0)),
                mix(box.min().y, box.max().y, random(seedLow, seedHigh, index, 1)),
                mix(box.min().z, box.max().z, random(seedLow, seedHigh, index, 2))
            );
            case EffectParticleShape.Disk disk -> diskSpawn(disk, seedLow, seedHigh, index);
            case EffectParticleShape.Line line -> lineSpawn(line, seedLow, seedHigh, index);
        };
    }

    static Vec3 velocity(EffectParticleMotion motion, EffectParticleShape shape, Vec3 spawn,
        int seedLow, int seedHigh, int index) {
        Vec3 randomDirection = randomDirection(seedLow, seedHigh, index, 10);
        Vec3 direction = switch (motion.mode()) {
            case DIRECTIONAL -> directional(motion.direction(), randomDirection, motion.spread());
            case OUTWARD -> outward(shapeCenter(shape), spawn, randomDirection);
            case INWARD -> outward(shapeCenter(shape), spawn, randomDirection).scale(-1.0);
        };
        double speed = mix(motion.speed().min(), motion.speed().max(),
            random(seedLow, seedHigh, index, 12));
        return direction.scale(speed);
    }

    static Sample sample(EffectParticleField field, long effectSeed, double effectAge, int index) {
        Objects.requireNonNull(field, "field");
        long mixed = effectSeed ^ field.salt();
        int seedLow = (int)mixed;
        int seedHigh = (int)(mixed >>> 32);
        Vec3 spawn = spawn(field.shape(), seedLow, seedHigh, index);
        Vec3 velocity = velocity(field.motion(), field.shape(), spawn, seedLow, seedHigh, index);
        double particleAge = particleAge(field, seedLow, seedHigh, index, effectAge);
        if (Double.isNaN(particleAge)) {
            return new Sample(Vec3.ZERO, 0.0, 0, false);
        }
        double progress = Math.min(1.0, particleAge / field.lifetimeTicks());
        Vec3 position = position(field.motion(), spawn, velocity, particleAge,
            seedLow, seedHigh, index);
        double size = mix(field.appearance().size().start(), field.appearance().size().end(),
            progress);
        int color = lerpColor(field.appearance().colorStart(), field.appearance().colorEnd(),
            progress);
        return new Sample(position, size, color, true);
    }

    static double particleAge(EffectParticleField field, int seedLow, int seedHigh, int index,
        double effectAge) {
        if (field.mode() == EffectParticleField.Mode.BURST) {
            return effectAge < field.lifetimeTicks() ? effectAge : Double.NaN;
        }
        double lifetime = field.lifetimeTicks();
        double phase = random(seedLow, seedHigh, index, 2) * lifetime;
        double wrapped = (effectAge + phase) % lifetime;
        return wrapped < 0.0 ? wrapped + lifetime : wrapped;
    }

    static Vec3 position(EffectParticleMotion motion, Vec3 spawn, Vec3 velocity, double age,
        int seedLow, int seedHigh, int index) {
        double travel = travelDistance(motion.drag(), age);
        double x = spawn.x + velocity.x * travel;
        double y = spawn.y + velocity.y * travel - 0.5 * motion.gravity() * age * age;
        double z = spawn.z + velocity.z * travel;
        if (motion.turbulence() > 0.0) {
            double amplitude = motion.turbulence() * 0.5;
            x += amplitude * Math.sin(age * 0.7 + phase(seedLow, seedHigh, index, 13));
            y += amplitude * Math.sin(age * 0.9 + phase(seedLow, seedHigh, index, 14));
            z += amplitude * Math.sin(age * 1.1 + phase(seedLow, seedHigh, index, 15));
        }
        return new Vec3(x, y, z);
    }

    static double travelDistance(double drag, double age) {
        if (drag <= 0.0001) {
            return age;
        }
        return (1.0 - Math.exp(-drag * age)) / drag;
    }

    static Vec3 shapeCenter(EffectParticleShape shape) {
        return switch (shape) {
            case EffectParticleShape.Point point -> Vec3.ZERO;
            case EffectParticleShape.Sphere sphere -> sphere.center();
            case EffectParticleShape.Box box -> box.min().add(box.max()).scale(0.5);
            case EffectParticleShape.Disk disk -> disk.center();
            case EffectParticleShape.Line line -> line.start().add(line.end()).scale(0.5);
        };
    }

    static int lerpColor(int start, int end, double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        int alpha = channel(start, 24, end, t);
        int red = channel(start, 16, end, t);
        int green = channel(start, 8, end, t);
        int blue = channel(start, 0, end, t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    static double mix(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static Vec3 sphereSpawn(EffectParticleShape.Sphere sphere, int seedLow, int seedHigh,
        int index) {
        double z = random(seedLow, seedHigh, index, 0) * 2.0 - 1.0;
        double angle = random(seedLow, seedHigh, index, 1) * TWO_PI;
        double radial = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        double radius = sphere.surface()
            ? sphere.maxRadius()
            : mix(sphere.minRadius(), sphere.maxRadius(),
                Math.pow(random(seedLow, seedHigh, index, 2), 1.0 / 3.0));
        return sphere.center().add(new Vec3(
            Math.cos(angle) * radial * radius,
            Math.sin(angle) * radial * radius,
            z * radius
        ));
    }

    private static Vec3 diskSpawn(EffectParticleShape.Disk disk, int seedLow, int seedHigh,
        int index) {
        Vec3 normal = disk.normal().normalize();
        Vec3 tangent = tangent(normal);
        Vec3 bitangent = normal.cross(tangent);
        double angle = random(seedLow, seedHigh, index, 0) * TWO_PI;
        double radius = mix(disk.minRadius(), disk.maxRadius(),
            Math.sqrt(random(seedLow, seedHigh, index, 1)));
        return disk.center()
            .add(tangent.scale(Math.cos(angle) * radius))
            .add(bitangent.scale(Math.sin(angle) * radius));
    }

    private static Vec3 lineSpawn(EffectParticleShape.Line line, int seedLow, int seedHigh,
        int index) {
        Vec3 start = line.start();
        Vec3 end = line.end();
        Vec3 direction = end.subtract(start);
        Vec3 axis = direction.lengthSqr() < 1.0E-12
            ? new Vec3(0.0, 0.0, 1.0)
            : direction.normalize();
        Vec3 tangent = tangent(axis);
        Vec3 bitangent = axis.cross(tangent);
        double along = random(seedLow, seedHigh, index, 0);
        double angle = random(seedLow, seedHigh, index, 1) * TWO_PI;
        double radius = line.radius() * Math.sqrt(random(seedLow, seedHigh, index, 2));
        return start.add(direction.scale(along))
            .add(tangent.scale(Math.cos(angle) * radius))
            .add(bitangent.scale(Math.sin(angle) * radius));
    }

    private static Vec3 directional(Vec3 direction, Vec3 randomDirection, double spread) {
        if (direction.lengthSqr() < 1.0E-12) {
            return randomDirection;
        }
        return direction.normalize().scale(1.0 - spread).add(randomDirection.scale(spread))
            .normalize();
    }

    private static Vec3 outward(Vec3 center, Vec3 spawn, Vec3 fallback) {
        Vec3 direction = spawn.subtract(center);
        if (direction.lengthSqr() < 1.0E-12) {
            return fallback;
        }
        return direction.normalize();
    }

    private static Vec3 randomDirection(int seedLow, int seedHigh, int index, int stream) {
        double z = random(seedLow, seedHigh, index, stream) * 2.0 - 1.0;
        double angle = random(seedLow, seedHigh, index, stream + 1) * TWO_PI;
        double radial = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(Math.cos(angle) * radial, Math.sin(angle) * radial, z);
    }

    private static Vec3 tangent(Vec3 normal) {
        Vec3 reference = Math.abs(normal.y) > 0.9
            ? new Vec3(1.0, 0.0, 0.0)
            : new Vec3(0.0, 1.0, 0.0);
        return normal.cross(reference).normalize();
    }

    private static double phase(int seedLow, int seedHigh, int index, int stream) {
        return random(seedLow, seedHigh, index, stream) * TWO_PI;
    }

    private static int channel(int start, int shift, int end, double amount) {
        int from = start >>> shift & 0xFF;
        int to = end >>> shift & 0xFF;
        return (int)Math.round(from + (to - from) * amount);
    }

    /** One sampled particle in effect-local space. */
    record Sample(Vec3 position, double size, int color, boolean visible) {
    }
}
