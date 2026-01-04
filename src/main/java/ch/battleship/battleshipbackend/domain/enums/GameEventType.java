package ch.battleship.battleshipbackend.domain.enums;

/**
 * Defines the types of game events that can be published to clients (e.g. via WebSocket).
 *
 * <p>Used to communicate state changes such as game lifecycle transitions, board actions,
 * and turn/shot related updates.
 */
public enum GameEventType {
    BOARD_CONFIRMED,
    BOARD_REROLLED,
    GAME_STARTED,
    GAME_FINISHED,
    GAME_PAUSED,
    GAME_RESUMED,
    GAME_RESUME_PENDING,
    GAME_FORFEITED,
    SHOT_FIRED,
    TURN_CHANGED
}
