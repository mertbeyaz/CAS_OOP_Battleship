package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a chat message sent by a player during a game.
 *
 * <p>Chat messages are persisted to allow broadcasting via WebSocket and optional
 * retrieval via REST endpoints (e.g. loading chat history).
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    /**
     * Message text content. Limited in length to prevent oversized payloads.
     */
    @Column(nullable = false, length = 500)
    private String text;

    /**
     * The player who sent the message.
     *
     * <p>Loaded lazily to avoid unnecessary joins when only message metadata is required.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Player sender;

    /**
     * Creates a new chat message.
     *
     * @param text the message content
     * @param sender the player sending the message
     */
    public ChatMessage(String text, Player sender) {
        this.text = text;
        this.sender = sender;
    }
}
