package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@code Lobby} entities.
 */
public interface LobbyRepository extends JpaRepository<Lobby, UUID> {

    /**
     * Returns the oldest lobby with the given status.
     *
     * <p>Used to find an existing open lobby (FIFO) before creating a new one.
     *
     * @param status lobby status to filter by
     * @return the oldest matching lobby if present
     */
    Optional<Lobby> findFirstByStatusOrderByCreatedAtAsc(LobbyStatus status);
}
