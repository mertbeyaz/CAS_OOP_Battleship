package ch.battleship.battleshipbackend.web.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a chat message that is sent to clients via REST or WebSocket.
 *
 * <p>This DTO is client-facing and therefore contains only the information required to display
 * the message in the UI (sender identity, game context and timestamp).
 *
 * @param senderId id of the message sender
 * @param senderName username of the sender (display name)
 * @param gameCode game identifier the message belongs to
 * @param message chat message content
 * @param timestamp time when the message was created/sent
 */
public record ChatMessageDto(
        UUID senderId,
        String senderName,
        String gameCode,
        String message,
        Instant timestamp
) {}
