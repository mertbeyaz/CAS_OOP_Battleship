package ch.battleship.battleshipbackend.domain.enums;

/**
 * Defines the types of game events that can be published to clients (e.g. via WebSocket).
 *
 * <p>Used to communicate state changes such as game lifecycle transitions, board actions,
 * and turn/shot related updates.
 */
public enum GameEventType {
    /**
     * A player has confirmed (locked) their board setup.
     */
    BOARD_CONFIRMED,

    /**
     * A player has re-rolled their board (auto-placement).
     */
    BOARD_REROLLED,

    /**
     * The game has started and players can begin firing shots.
     */
    GAME_STARTED,

    /**
     * The game has finished and a winner has been determined.
     */
    GAME_FINISHED,

    /**
     * The game has been paused (manually or due to disconnect).
     */
    GAME_PAUSED,

    /**
     * The game has been resumed after being paused.
     */
    GAME_RESUMED,

    /**
     * A resume request is pending - waiting for the other player to confirm.
     */
    GAME_RESUME_PENDING,

    /**
     * A player has forfeited the game.
     */
    GAME_FORFEITED,

    /**
     * A shot has been fired at a target board.
     */
    SHOT_FIRED,

    /**
     * The turn has changed to the other player.
     */
    TURN_CHANGED,

    /**
     * A player has disconnected (WebSocket connection lost).
     * The game will be moved to WAITING status and both players need to resume.
     */
    PLAYER_DISCONNECTED,

    /**
     * A player has reconnected after being disconnected.
     * Does not automatically resume the game - both players still need to use their resume tokens.
     */
    PLAYER_RECONNECTED
}
