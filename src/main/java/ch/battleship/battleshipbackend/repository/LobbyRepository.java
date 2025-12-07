package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LobbyRepository extends JpaRepository<Lobby, UUID> {

    Optional<Lobby> findFirstByStatusOrderByCreatedAtAsc(LobbyStatus status);
}
