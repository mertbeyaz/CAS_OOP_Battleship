package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Game;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByGameCode(String gameCode);
}
