package mafisher.quasar.api.cinematic;

/** Where cinematic sound cues are played. */
public enum CinematicAudioMode {
    /** Server plays each sound when its cue executes; the client hears it after network latency. */
    SERVER,
    /** Server sends one effect audio plan and the client schedules cues against its own game time. */
    CLIENT
}
