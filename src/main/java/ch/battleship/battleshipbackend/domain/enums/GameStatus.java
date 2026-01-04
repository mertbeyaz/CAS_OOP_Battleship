package ch.battleship.battleshipbackend.domain.enums;

/**
 * Represents the lifecycle status of a game.
 *
 * <p>The status controls which actions are allowed (e.g. setup vs. shooting) and is used
 * by the backend to validate incoming requests.
 */
public enum GameStatus {
    /**
     * Initial state: game exists but is not yet ready for setup (e.g. waiting for players).
     */
    WAITING,

    /**
     * Setup phase: both players are present, but boards can still be (re)arranged.
     * Shooting is not allowed in this state.
     */
    SETUP,

    /**
     * Both boards are confirmed and the game can be started.
     */
    READY,

    /**
     * Main gameplay state. Shooting and turn handling are active.
     */
    RUNNING,

    /**
     * Game is temporarily suspended (e.g. disconnect / resume workflow).
     */
    PAUSED,

    /**
     * Terminal state: the game ended normally (win condition reached).
     */
    FINISHED
}
