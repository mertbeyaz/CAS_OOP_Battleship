package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.GameResumeToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link GameResumeToken} entities.
 *
 * <p>This repository is used to resolve a public resume token back to the owning
 * {@code (game, player)} pair, and to ensure that a player has at most one resume
 * token per game.
 *
 * <p>Typical usage:
 * <ul>
 *   <li>Lookup by token during "resume by token" flow.</li>
 *   <li>Lookup by (gameId, playerId) to reuse an existing token instead of creating
 *       a new one every time the player joins/rejoins.</li>
 * </ul>
 */
public interface GameResumeTokenRepository extends JpaRepository<GameResumeToken, UUID> {

    /**
     * Finds a resume token by its public token string.
     *
     * <p>The token value is intended to be shared with the client (similar to a session
     * or invite code). It must therefore be unique and hard to guess.
     *
     * @param token the public resume token string
     * @return the matching {@link GameResumeToken} if present; otherwise {@link Optional#empty()}
     */
    Optional<GameResumeToken> findByToken(String token);

    /**
     * Finds a resume token for a specific player in a specific game.
     *
     * <p>This is useful to enforce "one token per (game, player)" and to make token creation
     * idempotent: if a token already exists for the given pair, it can be returned instead of
     * creating a new record.
     *
     * @param gameId   the {@code Game.id} this token belongs to
     * @param playerId the {@code Player.id} this token belongs to
     * @return the existing {@link GameResumeToken} for the given pair if present; otherwise {@link Optional#empty()}
     */
    Optional<GameResumeToken> findByGame_IdAndPlayer_Id(UUID gameId, UUID playerId);
}
