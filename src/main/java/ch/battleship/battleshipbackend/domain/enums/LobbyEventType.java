package ch.battleship.battleshipbackend.domain.enums;

/**
 * Defines event types related to lobby state changes.
 *
 * <p>Used for publishing lobby updates to clients (e.g. via WebSocket).
 */
public enum LobbyEventType {

    /**
     * Indicates that the lobby reached its maximum player capacity.
     */
    LOBBY_FULL,

    /**
     * Reserved for future use: emitted when a player joins the lobby.
     *
     * <p>Note: Currently not used by the backend event publishing logic.
     */
    PLAYER_JOINED
}
