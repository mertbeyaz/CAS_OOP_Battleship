package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Resume token for a player in a specific game.
 *
 * <p>This token allows a client to re-enter a game without having to provide both
 * {@code gameCode} and {@code playerId}. The token is treated as a public identifier
 * and should therefore be unpredictable (UUID is sufficient for this project scope).
 *
 * <p>Tokens are stored separately to keep the {@code Game} and {@code Player} aggregates
 * unchanged and to avoid introducing new bidirectional mappings late in the project.
 */
@Entity
@Table(
        name = "game_resume_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_resume_token_token", columnNames = "token"),
                @UniqueConstraint(name = "uk_resume_token_game_player", columnNames = {"game_id", "player_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameResumeToken extends BaseEntity {

    /**
     * Public resume token. Must be unique.
     */
    @Column(nullable = false, unique = true, length = 128)
    private String token;

    /**
     * Game this token belongs to.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /**
     * Player this token belongs to.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /**
     * Timestamp of the last usage of this token (optional).
     *
     * <p>Useful for debugging and analytics (e.g., when a player last rejoined a game).
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Creates a new resume token for a given game and player.
     *
     * @param token  the public token string (must be unique)
     * @param game   the game the token belongs to
     * @param player the player the token belongs to
     */
    public GameResumeToken(String token, Game game, Player player) {
        this.token = token;
        this.game = game;
        this.player = player;
        this.lastUsedAt = null;
    }
}
