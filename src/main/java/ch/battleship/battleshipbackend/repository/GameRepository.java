package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@code Game} entities.
 *
 * <p>Provides standard CRUD operations via {@code JpaRepository} and custom queries
 * used by the service layer.
 */
// NOTE (dev): Spring Data erzeugt die Implementierung zur Laufzeit.
// Wird im GameService injected.
public interface GameRepository extends JpaRepository<Game, UUID> {

    /**
     * Finds a game by its public game code.
     *
     * @param gameCode public identifier shared with clients
     * @return the matching game if found
     */
    Optional<Game> findByGameCode(String gameCode);
}
