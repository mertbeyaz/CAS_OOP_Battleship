package ch.battleship.battleshipbackend.web.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
        UUID senderId,
        String senderName,
        String gameCode,
        String message,
        Instant timestamp
) {}

