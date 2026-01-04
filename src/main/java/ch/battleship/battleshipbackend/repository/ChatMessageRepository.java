package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for persisting and querying {@code ChatMessage} entities.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
}
