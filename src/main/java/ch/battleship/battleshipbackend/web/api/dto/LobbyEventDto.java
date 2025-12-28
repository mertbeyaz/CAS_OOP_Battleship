package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.LobbyEventType;

public record LobbyEventDto(
        LobbyEventType type,
        String lobbyCode,
        String gameCode,
        String status,
        String joinedUsername
) {}
