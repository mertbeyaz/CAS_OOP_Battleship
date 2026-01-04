package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.LobbyEventType;

/**
 * WebSocket event DTO used to notify clients about lobby state changes.
 *
 * <p>Lobby events are typically published to a lobby-specific topic
 * (e.g. {@code /topic/lobbies/{lobbyCode}/events}) and inform subscribed clients
 * about matchmaking-related changes.
 *
 * <p>This DTO is intentionally lightweight and contains only the information
 * required by the frontend to react to lobby transitions.
 *
 * @param type type of lobby event
 * @param lobbyCode public identifier of the lobby
 * @param gameCode public identifier of the associated game
 * @param status current lobby status (enum name)
 * @param joinedUsername username of the player that caused the event (e.g. joined the lobby)
 */
public record LobbyEventDto(
        LobbyEventType type,
        String lobbyCode,
        String gameCode,
        String status,
        String joinedUsername
) {}
