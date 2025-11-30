package ch.battleship.battleshipbackend.web.api.dto;

import java.util.List;
import java.util.UUID;

public record BoardStateDto(
        UUID boardId,
        int width,
        int height,
        UUID ownerId,
        String ownerUsername,
        List<ShipPlacementDto> ships,
        List<ShotDto> shotsOnThisBoard
) { }
