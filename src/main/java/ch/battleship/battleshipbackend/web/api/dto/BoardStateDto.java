package ch.battleship.battleshipbackend.web.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BoardStateDto(
        UUID boardId,
        int width,
        int height,
        UUID ownerId,
        String ownerUsername,
        boolean locked,
        List<ShipPlacementDto> ships,
        List<Map<String, Object>> shotsOnThisBoard
) { }
