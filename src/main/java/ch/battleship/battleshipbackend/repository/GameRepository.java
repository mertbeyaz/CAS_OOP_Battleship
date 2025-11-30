package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Game;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA Repository. Schicht über JPA/Hibernate
// Wird zur Laufzeit in GameService injected
// und stellt Methoden wie: save, findbyId, findAll, delete, deleteById, count zur Verfügung
public interface GameRepository extends JpaRepository<Game, UUID> {
    Optional<Game> findByGameCode(String gameCode);
}
