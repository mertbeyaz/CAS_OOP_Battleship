package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Shot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for persisting and querying {@code Shot} entities.
 */
public interface ShotRepository extends JpaRepository<Shot, UUID> {
}
