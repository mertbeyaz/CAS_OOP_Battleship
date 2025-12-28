package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

import java.util.UUID;

public record JoinGameResponseDto(
        String gameCode,
        UUID playerId,
        String playerName,
        GameStatus status
) {}
