package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/**
 * Represents a Battleship game instance including configuration, participating players,
 * boards, shot history and chat messages.
 *
 * <p>The game aggregates the main runtime state (status, current turn, winner) and provides
 * helper methods to update the domain state. Rule enforcement such as authorization and
 * turn validation is handled in the service layer.
 */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity {

    /**
     * Current lifecycle status of the game (setup, running, finished, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    /**
     * Public game identifier used by clients to join and load a game.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String gameCode;

    /**
     * Game configuration such as board size and ship setup.
     */
    @Embedded
    private GameConfiguration config;

    /**
     * Players participating in this game.
     *
     * Design note: Relationship is intentionally unidirectional (Game -> Player) to keep
     * the domain model simple and avoid back references during serialization/mapping.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id") // FK column in players table (Player does not reference Game)
    private List<Player> players = new ArrayList<>();

    /**
     * Boards belonging to this game (typically one per player).
     *
     * Design note: Relationship is intentionally unidirectional (Game -> Board).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id") // FK column in boards table (Board does not reference Game)
    private List<Board> boards = new ArrayList<>();

    /**
     * Shot history for this game.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id")
    private List<Shot> shots = new ArrayList<>();

    /**
     * Chat messages sent during this game.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id")
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * Player id whose turn it currently is.
     */
    @Column(name = "current_turn_player_id")
    private UUID currentTurnPlayerId;

    /**
     * Player id that is ready to resume (used for pause/resume flow).
     */
    @Column(name = "resume_ready_player_id")
    private UUID resumeReadyPlayerId;

    /**
     * Player id of the winner, set when the game is finished.
     */
    @Column(name = "winner_player_id")
    private UUID winnerPlayerId;

    /**
     * Creates a new game using a random game code.
     *
     * @param config game configuration
     */
    public Game(GameConfiguration config) {
        this.status = GameStatus.WAITING;
        this.gameCode = UUID.randomUUID().toString();
        this.config = config;
    }

    /**
     * Creates a new game using a predefined game code (e.g. for testing or deterministic setups).
     *
     * @param gameCode public game identifier
     * @param config game configuration
     */
    public Game(String gameCode, GameConfiguration config) {
        this.status = GameStatus.WAITING;
        this.gameCode = gameCode;
        this.config = config;
    }

    /**
     * Adds a player to this game.
     *
     * @param player player to add
     */
    public void addPlayer(Player player) {
        this.players.add(player);
    }

    /**
     * Adds a board to this game.
     *
     * @param board board to add
     */
    public void addBoard(Board board) {
        this.boards.add(board);
    }

    /**
     * Adds a shot to the shot history.
     *
     * @param shot shot to add
     */
    public void addShot(Shot shot) {
        this.shots.add(shot);
    }

    /**
     * Fires a shot against a target board at the given coordinate and returns the resulting shot entry.
     *
     * Rules implemented here:
     * - If the coordinate was shot before on the same target board, the result is ALREADY_SHOT.
     * - If no ship occupies the coordinate, the result is MISS.
     * - If a ship occupies the coordinate, the result is HIT or SUNK depending on whether all
     *   coordinates of the ship have been hit after this shot.
     *
     * Note: Turn validation and authorization (who may shoot when) is typically enforced
     * by the service layer, not by this entity method.
     *
     * @param shooter player firing the shot
     * @param targetBoard board being targeted
     * @param coordinate target coordinate (0-based)
     * @return the created shot instance including the computed result
     */
    public Shot fireShot(Player shooter, Board targetBoard, Coordinate coordinate) {

        // 1) Check if this coordinate was already targeted on the same board
        boolean alreadyShot = shots.stream()
                .filter(s -> s.getTargetBoard().equals(targetBoard))
                .anyMatch(s -> s.getCoordinate().equals(coordinate));

        if (alreadyShot) {
            Shot shot = new Shot(coordinate, ShotResult.ALREADY_SHOT, shooter, targetBoard);
            addShot(shot);
            return shot;
        }

        // 2) Determine hit/miss
        boolean hit = targetBoard.getPlacements().stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .anyMatch(c -> c.equals(coordinate));

        if (!hit) {
            Shot shot = new Shot(coordinate, ShotResult.MISS, shooter, targetBoard);
            addShot(shot);
            return shot;
        }

        // 3) Find the ship placement that was hit (should exist because hit == true)
        ShipPlacement hitPlacement = targetBoard.getPlacements().stream()
                .filter(p -> p.getCoveredCoordinates().contains(coordinate))
                .findFirst()
                .orElseThrow();

        // 4) Determine if the ship is now sunk (all coordinates of the placement were hit)
        boolean allCoordsOfShipHit = hitPlacement.getCoveredCoordinates().stream()
                .allMatch(shipCoord ->
                        shots.stream()
                                .filter(s -> s.getTargetBoard().equals(targetBoard))
                                .anyMatch(s -> s.getCoordinate().equals(shipCoord))
                                || shipCoord.equals(coordinate) // include the current shot
                );

        ShotResult result = allCoordsOfShipHit ? ShotResult.SUNK : ShotResult.HIT;
        Shot shot = new Shot(coordinate, result, shooter, targetBoard);
        addShot(shot);
        return shot;
    }

    /**
     * Adds a chat message to this game.
     *
     * @param message message to add
     */
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }
}
