package ch.battleship.battleshipbackend.web.api.dto;

import java.util.UUID;

public record ShotRequest(
        UUID shooterId,
        int x,
        int y
) { }