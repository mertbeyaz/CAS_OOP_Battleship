package ch.battleship.battleshipbackend.domain.enums;

/**
 * Represents the current capacity status of a lobby.
 */
public enum LobbyStatus {
    /**
     * Lobby is open and can accept additional players.
     */
    WAITING,

    /**
     * Lobby reached its maximum player capacity.
     */
    FULL
}
