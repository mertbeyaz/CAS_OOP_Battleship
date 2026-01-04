package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a lobby used to coordinate players before and during game start.
 *
 * <p>The lobby has a public {@code lobbyCode} used by clients to join. Each lobby is
 * linked to exactly one {@code Game} instance.
 *
 * <p>Concurrency note:
 * This entity uses optimistic locking to prevent race conditions when multiple players
 * join concurrently (e.g. both trying to claim the last available slot).
 */
@Entity
@Table(name = "lobbies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lobby extends BaseEntity {

    /**
     * Public lobby identifier used by clients (similar to {@code gameCode}).
     */
    @Column(nullable = false, unique = true, updatable = false)
    private String lobbyCode;

    /**
     * Current lobby capacity state (waiting vs. full).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LobbyStatus status;

    /**
     * The game instance associated with this lobby (1:1 relationship).
     */
    @OneToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false, unique = true)
    private Game game;

    /**
     * Optimistic locking version field to detect concurrent updates.
     *
     * <p>Used to guard against race conditions during join operations.
     */
    @Version
    private long version;

    /**
     * Creates a new lobby with a generated lobby code.
     *
     * @param game the associated game instance
     */
    public Lobby(Game game) {
        this.lobbyCode = UUID.randomUUID().toString();
        this.status = LobbyStatus.WAITING;
        this.game = Objects.requireNonNull(game);
    }

    /**
     * Creates a new lobby with a predefined lobby code (e.g. for tests).
     *
     * @param lobbyCode public lobby identifier
     * @param game the associated game instance
     */
    public Lobby(String lobbyCode, Game game) {
        this.lobbyCode = Objects.requireNonNull(lobbyCode);
        this.status = LobbyStatus.WAITING;
        this.game = Objects.requireNonNull(game);
    }
}
