package ch.battleship.battleshipbackend.web.api.dto;

import java.util.UUID;

public record PlayerActionRequest(UUID playerId) {}