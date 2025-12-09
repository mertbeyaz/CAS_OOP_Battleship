package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
}

