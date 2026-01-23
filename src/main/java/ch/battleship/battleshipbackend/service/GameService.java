package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.GameResumeTokenRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.web.api.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Application service implementing the main Battleship use-cases.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create games and allow players to join</li>
 *   <li>Provide client views (public state and snapshots)</li>
 *   <li>Handle game actions (board confirmation, reroll, firing shots)</li>
 *   <li>Manage pause/resume/forfeit flows</li>
 *   <li>Publish game events to clients via WebSocket topics</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>This service is transactional to ensure game state updates are persisted consistently.</li>
 *   <li>Validation is performed here to prevent illegal state changes (wrong status, wrong turn, etc.).</li>
 *   <li>DTO mapping intentionally hides hidden information (e.g. opponent ship placements) to prevent cheating.</li>
 * </ul>
 */
@Service
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ShotRepository shotRepository;
    private final GameResumeTokenRepository gameResumeTokenRepository;

    /**
     * Creates a new {@code GameService}.
     *
     * @param gameRepository repository for game persistence
     * @param messagingTemplate WebSocket messaging template for event publishing (may be null in tests)
     * @param shotRepository repository for shot persistence
     */
    public GameService(GameRepository gameRepository,
                       SimpMessagingTemplate messagingTemplate,
                       ShotRepository shotRepository, GameResumeTokenRepository gameResumeTokenRepository) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
        this.shotRepository = shotRepository;
        this.gameResumeTokenRepository = gameResumeTokenRepository;

    }

    // =========================
    // 1) QUERIES (read-only)
    // =========================

    /**
     * Loads a game by its public game code.
     *
     * @param gameCode public identifier shared with clients
     * @return matching game if present
     */
    public Optional<Game> getByGameCode(String gameCode) {
        return gameRepository.findByGameCode(gameCode);
    }

    /**
     * Returns a sanitized, client-safe view of the current game state.
     *
     * <p>This view intentionally does not expose internal identifiers or hidden information
     * that could be used for cheating.
     *
     * @param gameCode public game identifier
     * @param playerId requesting player id
     * @return public game state for the requesting player
     * @throws EntityNotFoundException if the game does not exist
     */
    public GamePublicDto getPublicState(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));
        return toPublicDto(game, playerId);
    }

    /**
     * Returns a detailed snapshot for the requesting player.
     *
     * <p>The snapshot includes own board state and shot history while keeping opponent
     * ship placements hidden to prevent cheating.
     *
     * @param gameCode public game identifier
     * @param playerId requesting player id
     * @return snapshot view for the requesting player
     * @throws EntityNotFoundException if the game does not exist
     */
    public GameSnapshotDto getSnapshot(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        return toSnapshot(game, playerId);
    }

    // =========================
    // 2) COMMANDS (game lifecycle)
    // =========================

    // --- Create / Join ---

    /**
     * Creates a new game using the default configuration.
     *
     * @return persisted game instance
     */
    public Game createNewGame() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);
        return gameRepository.save(game);
    }

    /**
     * Creates a game and immediately joins the first player.
     *
     * @param username username of the first player
     * @return updated game including the first joined player and board
     */
    @Transactional
    public Game createGameAndJoinFirstPlayer(String username) {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);
        game = gameRepository.save(game);
        return joinGame(game.getGameCode(), username);
    }

    /**
     * Public join operation returning the data required by clients for subsequent requests.
     *
     * <p>Returns the generated player id which acts as the client identity within the game.
     *
     * @param gameCode public identifier of the game
     * @param username chosen display name
     * @return join response including player id and current game status
     */
    public JoinGameResponseDto joinGamePublic(String gameCode, String username) {
        Game game = joinGame(gameCode, username);

        Player joined = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> username.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Joined player not found"));

        return new JoinGameResponseDto(
                game.getGameCode(),
                joined.getId(),
                joined.getUsername(),
                game.getStatus()
        );
    }

    /**
     * Internal join logic (kept as-is). Controller should usually use {@link #joinGamePublic(String, String)}.
     *
     * <p>Creates a player and an initial board with an auto-placed fleet.
     * The game must be in WAITING state and may only contain up to two players.
     *
     * @param gameCode public identifier of the game to join
     * @param username chosen display name (must be unique within the game)
     * @return updated and persisted game
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the game is not joinable
     */
    public Game joinGame(String gameCode, String username) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("Cannot join a game that is not in WAITING state");
        }

        int currentPlayers = game.getPlayers().size();
        if (currentPlayers >= 2) {
            throw new IllegalStateException("Game already has 2 players");
        }

        // Prevent duplicate usernames in the same game (simplifies client UX and state mapping).
        boolean nameExists = game.getPlayers().stream()
                .anyMatch(p -> p != null && username.equals(p.getUsername()));
        if (nameExists) {
            throw new IllegalStateException("Username already exists in this game");
        }

        Player player = new Player(username);
        game.addPlayer(player);

        Board board = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                player
        );

        autoPlaceFleet(board, game.getConfig());
        game.addBoard(board);

        // When the second player joins, the game enters SETUP (boards may be rerolled/confirmed).
        if (currentPlayers + 1 == 2) {
            game.setStatus(GameStatus.SETUP);
        }

        return gameRepository.save(game);
    }

    // --- Setup phase ---

    /**
     * Re-generates a player's board during the setup phase.
     *
     * @param gameCode public game identifier
     * @param playerId board owner
     * @return board state including current placements
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the game is not in SETUP, the player has no board, or the board is locked
     */
    public BoardStateDto rerollBoard(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.SETUP) {
            throw new IllegalStateException("Cannot reroll board when game is not in SETUP");
        }

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getOwner().getId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not have a board in this game"));

        if (board.isLocked()) {
            throw new IllegalStateException("Board is locked and cannot be rerolled");
        }

        board.clearPlacements();
        autoPlaceFleet(board, game.getConfig());

        gameRepository.save(game);

        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.isLocked(),
                board.getPlacements().stream().map(ShipPlacementDto::from).toList()
        );
    }

    /**
     * Locks a player's board (confirmation) and starts the game once both boards are confirmed.
     *
     * <p>When the second board is confirmed, the game transitions to RUNNING and the initial turn
     * player is selected randomly if not already set.
     *
     * @param gameCode public game identifier
     * @param playerId confirming player id
     * @return public state view for the confirming player
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the game is not in SETUP, board is invalid, or already locked
     */
    public GamePublicDto confirmBoard(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.SETUP) {
            throw new IllegalStateException("Cannot confirm board when game is not in SETUP");
        }

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getOwner().getId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not have a board in this game"));

        int expectedShips = parseFleetDefinition(game.getConfig()).size();
        if (board.getPlacements().size() != expectedShips) {
            throw new IllegalStateException(
                    "Board is not ready: expected " + expectedShips + " ships but found " + board.getPlacements().size()
            );
        }

        if (board.isLocked()) {
            throw new IllegalStateException("Board is already confirmed");
        }

        board.setLocked(true);

        boolean allLocked = game.getBoards().stream().allMatch(Board::isLocked);
        if (allLocked) {
            game.setStatus(GameStatus.RUNNING);

            if (game.getCurrentTurnPlayerId() == null) {
                List<Player> players = game.getPlayers();
                if (players.size() != 2) {
                    throw new IllegalStateException("Cannot start game without exactly 2 players");
                }
                int idx = ThreadLocalRandom.current().nextInt(players.size());
                game.setCurrentTurnPlayerId(players.get(idx).getId());
            }
        }

        Game saved = gameRepository.save(game);

        if (messagingTemplate != null) {
            String destination = "/topic/games/" + gameCode + "/events";

            Player confirmingPlayer = saved.getPlayers().stream()
                    .filter(p -> Objects.equals(p.getId(), playerId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Player not found in this game"));

            messagingTemplate.convertAndSend(destination, GameEventDto.boardConfirmed(saved, confirmingPlayer));

            if (allLocked) {
                Player currentTurnPlayer = saved.getPlayers().stream()
                        .filter(p -> Objects.equals(p.getId(), saved.getCurrentTurnPlayerId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Current turn player not found"));

                messagingTemplate.convertAndSend(destination, GameEventDto.gameStarted(saved, currentTurnPlayer));
            }
        }

        return toPublicDto(saved, playerId);
    }

    // --- Running phase ---

    /**
     * Fires a shot for the current turn player and applies turn and win logic.
     *
     * <p>Validates:
     * <ul>
     *   <li>game is RUNNING</li>
     *   <li>current turn is set</li>
     *   <li>requester is the current turn player</li>
     *   <li>coordinates are within opponent board bounds</li>
     * </ul>
     *
     * <p>Turn rule:
     * <ul>
     *   <li>A MISS switches the turn to the opponent.</li>
     *   <li>HIT/SUNK keeps the turn (classic Battleship rule).</li>
     * </ul>
     *
     * @param gameCode public game identifier
     * @param shooterId player firing the shot (must match current turn)
     * @param x 0-based x coordinate
     * @param y 0-based y coordinate
     * @return persisted shot entity
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the shot is not allowed (wrong state/turn/player)
     * @throws IllegalArgumentException if coordinates are out of bounds
     */
    public Shot fireShot(String gameCode, UUID shooterId, int x, int y) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.RUNNING) {
            throw new IllegalStateException("Cannot fire shot when game is not RUNNING");
        }

        if (game.getCurrentTurnPlayerId() == null) {
            throw new IllegalStateException("Game has no current turn player set");
        }

        if (!Objects.equals(game.getCurrentTurnPlayerId(), shooterId)) {
            throw new IllegalStateException("It is not this player's turn");
        }

        Player shooter = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), shooterId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Shooter does not belong to this game"));

        Board targetBoard = game.getBoards().stream()
                .filter(b -> b.getOwner() != null && b.getOwner().getId() != null)
                .filter(b -> !Objects.equals(b.getOwner().getId(), shooterId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No opponent board found for this game"));

        if (x < 0 || x >= targetBoard.getWidth() || y < 0 || y >= targetBoard.getHeight()) {
            throw new IllegalArgumentException("Shot coordinate out of board bounds");
        }

        Coordinate coordinate = new Coordinate(x, y);
        Shot shot = game.fireShot(shooter, targetBoard, coordinate);

        Shot persistedShot = shotRepository.save(shot);

        // Turn rule: a MISS switches the turn, HIT/SUNK keeps the turn.
        if (shot.getResult() == ShotResult.MISS) {
            UUID nextTurn = game.getPlayers().stream()
                    .filter(p -> !Objects.equals(p.getId(), shooterId))
                    .map(Player::getId)
                    .findFirst()
                    .orElse(shooterId);
            game.setCurrentTurnPlayerId(nextTurn);
        }

        // Win check: defender has lost if all ship coordinates were hit on the target board.
        boolean defenderAllSunk = targetBoard.getPlacements().stream()
                .allMatch(p -> p.getCoveredCoordinates().stream().allMatch(shipCoord ->
                        game.getShots().stream()
                                .filter(s -> s.getTargetBoard().equals(targetBoard))
                                .anyMatch(s -> s.getCoordinate().equals(shipCoord))
                                || shipCoord.equals(coordinate)
                ));

        if (defenderAllSunk) {
            game.setStatus(GameStatus.FINISHED);
            game.setWinnerPlayerId(shooter.getId());
        }

        Game savedGame = gameRepository.save(game);

        if (messagingTemplate != null) {
            String destination = "/topic/games/" + gameCode + "/events";
            Player defender = targetBoard.getOwner();

            messagingTemplate.convertAndSend(destination,
                    GameEventDto.shotFired(savedGame, shooter, defender, x, y, shot.getResult())
            );

            if (savedGame.getStatus() == GameStatus.FINISHED) {
                messagingTemplate.convertAndSend(destination,
                        GameEventDto.gameFinished(savedGame, shooter)
                );
            } else {
                Player currentTurn = savedGame.getPlayers().stream()
                        .filter(p -> Objects.equals(p.getId(), savedGame.getCurrentTurnPlayerId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Current turn player not found"));

                messagingTemplate.convertAndSend(destination,
                        GameEventDto.turnChanged(savedGame, currentTurn, shot.getResult())
                );
            }
        }

        return persistedShot;
    }

    // --- Pause / Resume / Forfeit ---

    /**
     * Pauses a running game and notifies clients via WebSocket.
     *
     * @param gameCode public game identifier
     * @param requestedByPlayerId player requesting the pause
     * @return persisted game state
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the game is not running or player is not part of the game
     */
    public Game pauseGame(String gameCode, UUID requestedByPlayerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.RUNNING) {
            throw new IllegalStateException("Can only pause a RUNNING game");
        }

        Player requestedBy = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), requestedByPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not belong to this game"));

        game.setStatus(GameStatus.PAUSED);
        game.setResumeReadyPlayerId(null);

        Game saved = gameRepository.save(game);

        if (messagingTemplate != null) {
            String destination = "/topic/games/" + gameCode + "/events";
            messagingTemplate.convertAndSend(destination, GameEventDto.gamePaused(saved, requestedBy));
        }

        return saved;
    }

    /**
     * Resumes a paused game using a two-player handshake, identified by a public resume token.
     *
     * <p>Resume flow:
     * <ul>
     *   <li>First player confirms resume: status changes from PAUSED to WAITING and stores the confirmer id.</li>
     *   <li>Second player confirms resume: status changes from WAITING to RUNNING and clears the confirmer id.</li>
     *   <li>Special case: RUNNING with disconnected player → treated as PAUSED for resume purposes</li>
     * </ul>
     *
     * <p>This prevents a single player from unilaterally resuming a game.</p>
     *
     * @param token public resume token for a (game, player) pair
     * @return response including whether the handshake is complete and a snapshot for the requester
     * @throws EntityNotFoundException if the token or game does not exist
     * @throws IllegalStateException if the resume operation is not allowed for the current state
     */
    public GameResumeResponseDto resumeGame(String token) {
        GameResumeToken tokenEntity = gameResumeTokenRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Resume token not found"));

        Game game = tokenEntity.getGame();
        Player requestedBy = tokenEntity.getPlayer();

        if (game == null) {
            throw new EntityNotFoundException("Game not found for resume token");
        }
        if (requestedBy == null || requestedBy.getId() == null) {
            throw new IllegalStateException("Resume token is not linked to a valid player");
        }

        UUID requestedByPlayerId = requestedBy.getId();

        // Optional but nice: update lastUsedAt for debugging/analytics
        tokenEntity.setLastUsedAt(java.time.Instant.now());
        gameResumeTokenRepository.save(tokenEntity);

        // Validate player belongs to this game (defensive check)
        Player resolvedRequestedBy = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), requestedByPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not belong to this game"));


        boolean isReconnectScenario = game.getStatus() == GameStatus.RUNNING &&
                isPlayerDisconnected(game, requestedByPlayerId);

        if (game.getStatus() == GameStatus.RUNNING && !isReconnectScenario) {
            // Normal RUNNING game (both players connected) → just return snapshot
            return new GameResumeResponseDto(
                    game.getGameCode(),
                    game.getStatus(),
                    true,
                    resolvedRequestedBy.getUsername(),
                    getCurrentTurnPlayerName(game),
                    toSnapshot(game, requestedByPlayerId)
            );
        }

        // WAITING is allowed only if it's part of the resume-handshake
        if (game.getStatus() == GameStatus.WAITING && game.getResumeReadyPlayerId() == null) {
            throw new IllegalStateException("WAITING game cannot be resumed (not in resume-handshake)");
        }

        if (game.getStatus() != GameStatus.PAUSED &&
                game.getStatus() != GameStatus.WAITING &&
                !isReconnectScenario) {
            throw new IllegalStateException("Can only resume a PAUSED/WAITING game");
        }

        UUID first = game.getResumeReadyPlayerId();

        // Resume handshake: requires both players to confirm resume.
        Game saved;

        if (game.getStatus() == GameStatus.PAUSED || isReconnectScenario) {
            // Transition to WAITING (handshake step 1)
            game.setStatus(GameStatus.WAITING);
            game.setResumeReadyPlayerId(requestedByPlayerId);

            saved = gameRepository.save(game);
            sendEventResumed(saved, resolvedRequestedBy);
        } else {
            // status == WAITING
            if (Objects.equals(first, requestedByPlayerId)) {
                // Same player again -> idempotent
                saved = game;
            } else {
                // Second player confirms -> RUNNING
                game.setStatus(GameStatus.RUNNING);
                game.setResumeReadyPlayerId(null);

                saved = gameRepository.save(game);
                sendEventResumed(saved, resolvedRequestedBy);
            }
        }

        boolean handshakeComplete = saved.getStatus() == GameStatus.RUNNING;
        String turnName = handshakeComplete ? getCurrentTurnPlayerName(saved) : null;

        return new GameResumeResponseDto(
                saved.getGameCode(),
                saved.getStatus(),
                handshakeComplete,
                resolvedRequestedBy.getUsername(),
                turnName,
                toSnapshot(saved, requestedByPlayerId)
        );
    }

    /**
     * Forfeits a running/paused game, determines the winner and notifies clients.
     *
     * @param gameCode public game identifier
     * @param forfeitingPlayerId player who forfeits the game
     * @return persisted game state
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalStateException if the game is not forfeitable or winner cannot be determined
     */
    public Game forfeitGame(String gameCode, UUID forfeitingPlayerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.RUNNING && game.getStatus() != GameStatus.PAUSED) {
            throw new IllegalStateException("Can only forfeit a RUNNING or PAUSED game");
        }

        Player forfeitingPlayer = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), forfeitingPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not belong to this game"));

        Player winner = game.getPlayers().stream()
                .filter(p -> !Objects.equals(p.getId(), forfeitingPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot determine winner"));

        game.setStatus(GameStatus.FINISHED);
        game.setWinnerPlayerId(winner.getId());

        Game saved = gameRepository.save(game);

        if (messagingTemplate != null) {
            String destination = "/topic/games/" + gameCode + "/events";
            messagingTemplate.convertAndSend(destination, GameEventDto.gameForfeited(saved, forfeitingPlayer, winner));
            messagingTemplate.convertAndSend(destination, GameEventDto.gameFinished(saved, winner));
        }

        return saved;
    }

    // =========================
    // 3) DEV-ONLY
    // =========================

    /**
     * DEV-only: Returns full board state (including ship placements) for debugging.
     *
     * <p>Must not be exposed in production because it reveals hidden information.
     *
     * @param gameCode public game identifier
     * @param boardId board identifier
     * @return full board state
     */
    public BoardStateDto getBoardState(String gameCode, UUID boardId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getId(), boardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.isLocked(),
                board.getPlacements().stream().map(ShipPlacementDto::from).toList()
        );
    }

    /**
     * DEV-only: Renders an ASCII representation of the board.
     *
     * @param gameCode public game identifier
     * @param boardId board identifier
     * @param showShips if {@code true}, ship positions are rendered (debugging only)
     * @return ASCII board representation
     */
    public String getBoardAscii(String gameCode, UUID boardId, boolean showShips) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getId(), boardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        List<Shot> shotsOnThisBoard = game.getShots().stream()
                .filter(s -> s.getTargetBoard().equals(board))
                .toList();

        return renderBoardAscii(board, shotsOnThisBoard, showShips);
    }

    // =========================
    // 4) HELPERS
    // =========================

    // --- WebSocket event helpers ---

    /**
     * Sends a "resume pending" event to clients.
     *
     * @param game current game
     * @param requestedBy player who requested the resume
     */
    private void sendEventWaiting(Game game, Player requestedBy) {
        if (messagingTemplate == null) return;
        messagingTemplate.convertAndSend("/topic/games/" + game.getGameCode() + "/events",
                GameEventDto.gameResumePending(game, requestedBy));
    }

    /**
     * Sends a "game resumed" event to clients.
     *
     * @param game current game
     * @param requestedBy player who triggered the resume step
     */
    private void sendEventResumed(Game game, Player requestedBy) {
        if (messagingTemplate == null) return;

        Player currentTurnPlayer = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), game.getCurrentTurnPlayerId()))
                .findFirst()
                .orElse(null);

        messagingTemplate.convertAndSend("/topic/games/" + game.getGameCode() + "/events",
                GameEventDto.gameResumed(game, requestedBy,
                        currentTurnPlayer == null ? null : currentTurnPlayer.getUsername()));
    }

    // --- DTO mapping ---

    /**
     * Maps a {@code Game} to a public DTO that is safe for clients.
     *
     * <p>Intentionally hides information that could be used for cheating:
     * opponent ship placements are not included and only minimal state is returned.
     *
     * @param game current game
     * @param requesterPlayerId requesting player id
     * @return public view DTO for the requester
     */
    private GamePublicDto toPublicDto(Game game, UUID requesterPlayerId) {
        // Extract common game state using helper methods
        Player opponent = findOpponent(game, requesterPlayerId);
        Map<UUID, Board> boardByOwnerId = buildBoardMap(game);

        Board yourBoard = boardByOwnerId.get(requesterPlayerId);
        Board oppBoard = (opponent == null) ? null : boardByOwnerId.get(opponent.getId());

        boolean yourBoardLocked = yourBoard != null && yourBoard.isLocked();
        boolean opponentBoardLocked = oppBoard != null && oppBoard.isLocked();
        boolean yourTurn = isYourTurn(game, requesterPlayerId);

        String opponentName = (opponent == null) ? null : opponent.getUsername();

        return new GamePublicDto(
                game.getGameCode(),
                game.getStatus(),
                yourBoardLocked,
                opponentBoardLocked,
                yourTurn,
                opponentName
        );
    }

    /**
     * Maps a {@code Game} to a snapshot DTO for a specific viewer.
     *
     * <p>Snapshot rules:
     * <ul>
     *   <li>Own board: ship placements are visible.</li>
     *   <li>Opponent board: ship placements remain hidden.</li>
     *   <li>Shots: include shots on own board and shots fired by the viewer on opponent board.</li>
     * </ul>
     *
     * @param game current game
     * @param viewerPlayerId viewer player id
     * @return snapshot DTO for the viewer
     */
    private GameSnapshotDto toSnapshot(Game game, UUID viewerPlayerId) {
        // Extract common game state
        Player you = findViewer(game, viewerPlayerId);
        Player opponent = findOpponent(game, viewerPlayerId);
        Map<UUID, Board> boardByOwnerId = buildBoardMap(game);

        Board yourBoard = boardByOwnerId.get(viewerPlayerId);
        Board oppBoard = (opponent == null) ? null : boardByOwnerId.get(opponent.getId());

        boolean yourBoardLocked = yourBoard != null && yourBoard.isLocked();
        boolean oppBoardLocked = oppBoard != null && oppBoard.isLocked();
        boolean yourTurn = isYourTurn(game, viewerPlayerId);

        // Build shots lists
        List<Shot> allShots = game.getShots() == null ? List.of() : game.getShots();

        // Your board DTO with ship placements visible
        BoardStateDto yourBoardDto = (yourBoard == null)
                ? null
                : new BoardStateDto(
                yourBoard.getId(),
                yourBoard.getWidth(),
                yourBoard.getHeight(),
                yourBoard.isLocked(),
                yourBoard.getPlacements().stream().map(ShipPlacementDto::from).toList()
        );

        // Shots on your board (incoming shots from opponent)
        List<ShotViewDto> shotsOnYourBoard = (yourBoard == null)
                ? List.of()
                : allShots.stream()
                .filter(s -> s.getTargetBoard() != null && s.getTargetBoard().equals(yourBoard))
                .map(ShotViewDto::from)
                .toList();

        // Your shots on opponent board (outgoing shots you fired)
        List<ShotViewDto> yourShotsOnOpponent = (oppBoard == null)
                ? List.of()
                : allShots.stream()
                .filter(s -> s.getShooter() != null && Objects.equals(s.getShooter().getId(), viewerPlayerId))
                .filter(s -> s.getTargetBoard() != null && s.getTargetBoard().equals(oppBoard))
                .map(ShotViewDto::from)
                .toList();

        int w = game.getConfig().getBoardWidth();
        int h = game.getConfig().getBoardHeight();

        return new GameSnapshotDto(
                game.getGameCode(),
                game.getStatus(),
                w,
                h,
                viewerPlayerId.toString(),
                you.getUsername(),
                opponent == null ? null : opponent.getUsername(),
                yourBoardLocked,
                oppBoardLocked,
                yourTurn,
                yourBoardDto,
                shotsOnYourBoard,
                yourShotsOnOpponent
        );
    }

    // --- Domain navigation / utility ---

    /**
     * Returns the username of the current turn player, or null if no turn is set.
     *
     * @param game current game
     * @return username of current turn player, or null
     */
    private String getCurrentTurnPlayerName(Game game) {
        UUID id = game.getCurrentTurnPlayerId();
        if (id == null) return null;

        return game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), id))
                .map(Player::getUsername)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the opponent player for a given viewer in the game.
     *
     * @param game the game
     * @param viewerPlayerId ID of the viewer player
     * @return opponent player, or null if not found (single-player scenario or missing opponent)
     */
    private Player findOpponent(Game game, UUID viewerPlayerId) {
        return game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> !Objects.equals(p.getId(), viewerPlayerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the viewer player in the game.
     *
     * @param game the game
     * @param viewerPlayerId ID of the viewer player
     * @return viewer player
     * @throws IllegalStateException if viewer player is not found in the game
     */
    private Player findViewer(Game game, UUID viewerPlayerId) {
        return game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> Objects.equals(p.getId(), viewerPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not found in this game: " + viewerPlayerId));
    }

    /**
     * Builds a map of boards indexed by their owner's player ID.
     *
     * <p>Filters out null boards and boards without valid owners.
     *
     * @param game the game
     * @return map of player ID to board
     */
    private Map<UUID, Board> buildBoardMap(Game game) {
        Map<UUID, Board> boardByOwnerId = new HashMap<>();
        for (Board b : game.getBoards()) {
            if (b == null || b.getOwner() == null || b.getOwner().getId() == null) {
                continue;
            }
            boardByOwnerId.put(b.getOwner().getId(), b);
        }
        return boardByOwnerId;
    }

    /**
     * Determines if it's currently the viewer's turn.
     *
     * @param game the game
     * @param playerId ID of the player to check
     * @return true if game is running and it's the player's turn
     */
    private boolean isYourTurn(Game game, UUID playerId) {
        return game.getStatus() == GameStatus.RUNNING
                && game.getCurrentTurnPlayerId() != null
                && Objects.equals(game.getCurrentTurnPlayerId(), playerId);
    }

    // --- Fleet / board generation ---

    /**
     * Parses the fleet definition string into a flat list of ship types.
     *
     * <p>Expected format: {@code <count>x<size>} separated by commas, e.g. "2x2,2x3,1x4,1x5".
     *
     * @param config game configuration holding the fleet definition
     * @return list of ship types (one entry per ship instance)
     * @throws IllegalArgumentException if the fleet definition contains invalid parts or unsupported sizes
     */
    private List<ShipType> parseFleetDefinition(GameConfiguration config) {
        String definition = config.getFleetDefinition();
        List<ShipType> result = new ArrayList<>();

        if (definition == null || definition.isBlank()) {
            return result;
        }

        String[] parts = definition.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            String[] countAndSize = trimmed.split("x");
            if (countAndSize.length != 2) {
                throw new IllegalArgumentException("Invalid fleet definition part: " + trimmed);
            }

            int count = Integer.parseInt(countAndSize[0].trim());
            int size = Integer.parseInt(countAndSize[1].trim());

            ShipType type;
            switch (size) {
                case 2 -> type = ShipType.DESTROYER;
                case 3 -> type = ShipType.CRUISER;
                case 4 -> type = ShipType.BATTLESHIP;
                case 5 -> type = ShipType.CARRIER;
                default -> throw new IllegalArgumentException("Unsupported ship size in fleet definition: " + size);
            }

            for (int i = 0; i < count; i++) result.add(type);
        }

        return result;
    }

    /**
     * Creates a human-readable ASCII representation of a board for debugging.
     *
     * @param board board to render
     * @param shotsOnThisBoard shots that targeted this board
     * @param showShips if {@code true}, ship positions are rendered as 'S'
     * @return ASCII board view
     */
    private String renderBoardAscii(Board board, List<Shot> shotsOnThisBoard, boolean showShips) {
        int width = board.getWidth();
        int height = board.getHeight();

        char[][] grid = new char[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = '.';
            }
        }

        if (showShips) {
            for (ShipPlacement placement : board.getPlacements()) {
                for (Coordinate c : placement.getCoveredCoordinates()) {
                    int x = c.getX();
                    int y = c.getY();
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        grid[y][x] = 'S';
                    }
                }
            }
        }

        for (Shot shot : shotsOnThisBoard) {
            Coordinate c = shot.getCoordinate();
            int x = c.getX();
            int y = c.getY();

            if (x < 0 || x >= width || y < 0 || y >= height) continue;

            ShotResult result = shot.getResult();
            switch (result) {
                case MISS -> grid[y][x] = 'O';
                case HIT, SUNK -> grid[y][x] = 'X';
                case ALREADY_SHOT -> { }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Board (").append(width).append("x").append(height).append(")\n");

        sb.append("   ");
        for (int x = 0; x < width; x++) sb.append(x).append(' ');
        sb.append('\n');

        for (int y = 0; y < height; y++) {
            sb.append(String.format("%2d ", y));
            for (int x = 0; x < width; x++) sb.append(grid[y][x]).append(' ');
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Automatically places the complete fleet on the given board based on the configuration.
     *
     * <p>Uses a backtracking algorithm to guarantee a valid placement even with margin rules.
     *
     * @param board board to place the fleet on
     * @param config configuration defining fleet and margin rules
     * @throws IllegalStateException if a valid placement cannot be found for the given configuration
     */
    private void autoPlaceFleet(Board board, GameConfiguration config) {
        int margin = config.getShipMargin();
        List<ShipType> fleetTypes = parseFleetDefinition(config);

        List<Ship> fleet = new ArrayList<>();
        for (ShipType type : fleetTypes) fleet.add(new Ship(type));

        // Place larger ships first to reduce the chance of dead-ends.
        fleet.sort((a, b) -> Integer.compare(b.getType().getSize(), a.getType().getSize()));

        List<ShipPlacement> placements = new ArrayList<>();
        Random random = new Random();

        // Fleet placement uses backtracking to guarantee a valid placement under margin rules.
        // Greedy/random placement can fail for certain fleet/board combinations.
        boolean success = placeFleetBacktracking(board, fleet, 0, margin, placements, random);

        if (!success) {
            throw new IllegalStateException(String.format(
                    "Could not place fleet on board %dx%d with margin=%d. Check fleet definition vs board size.",
                    board.getWidth(), board.getHeight(), margin
            ));
        }

        for (ShipPlacement p : placements) {
            board.placeShip(p.getShip(), p.getStart(), p.getOrientation());
        }
    }

    /**
     * Backtracking algorithm that attempts to place all ships on the board.
     *
     * @param board target board
     * @param fleet list of ships to place
     * @param index current index in the fleet list
     * @param margin minimum spacing rule
     * @param currentPlacements working list of placements (mutated during backtracking)
     * @param random random source for shuffling candidate orders
     * @return {@code true} if a complete valid placement is found, otherwise {@code false}
     */
    private boolean placeFleetBacktracking(Board board,
                                           List<Ship> fleet,
                                           int index,
                                           int margin,
                                           List<ShipPlacement> currentPlacements,
                                           Random random) {
        if (index >= fleet.size()) return true;

        Ship ship = fleet.get(index);

        List<Orientation> orientations = new ArrayList<>(List.of(Orientation.values()));
        Collections.shuffle(orientations, random);

        List<Coordinate> candidates = new ArrayList<>();
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                candidates.add(new Coordinate(x, y));
            }
        }
        Collections.shuffle(candidates, random);

        for (Orientation orientation : orientations) {
            for (Coordinate start : candidates) {
                if (canPlaceShipWithMarginVirtual(board, ship, start, orientation, margin, currentPlacements)) {

                    ShipPlacement placement = new ShipPlacement(ship, start, orientation);
                    currentPlacements.add(placement);

                    if (placeFleetBacktracking(board, fleet, index + 1, margin, currentPlacements, random)) {
                        return true;
                    }

                    currentPlacements.remove(currentPlacements.size() - 1);
                }
            }
        }

        return false;
    }

    /**
     * Checks whether a ship can be placed while respecting the configured margin rule.
     *
     * <p>This method operates on a virtual list of placements used during backtracking (not the persisted board).
     *
     * @param board target board
     * @param ship ship to place
     * @param start start coordinate
     * @param orientation placement orientation
     * @param margin minimum spacing rule
     * @param currentPlacements already chosen placements in the backtracking process
     * @return {@code true} if placement is valid, otherwise {@code false}
     */
    private boolean canPlaceShipWithMarginVirtual(Board board,
                                                  Ship ship,
                                                  Coordinate start,
                                                  Orientation orientation,
                                                  int margin,
                                                  List<ShipPlacement> currentPlacements) {

        ShipPlacement candidate = new ShipPlacement(ship, start, orientation);
        List<Coordinate> newCoords = candidate.getCoveredCoordinates();

        int width = board.getWidth();
        int height = board.getHeight();

        for (Coordinate c : newCoords) {
            if (c.getX() < 0 || c.getX() >= width || c.getY() < 0 || c.getY() >= height) {
                return false;
            }
        }

        List<Coordinate> existingCoords = currentPlacements.stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .toList();

        for (Coordinate nc : newCoords) {
            for (Coordinate ec : existingCoords) {
                if (nc.getX() == ec.getX() && nc.getY() == ec.getY()) return false;
            }
        }

        if (margin > 0) {
            for (Coordinate nc : newCoords) {
                for (Coordinate ec : existingCoords) {
                    int dx = Math.abs(nc.getX() - ec.getX());
                    int dy = Math.abs(nc.getY() - ec.getY());
                    if (dx <= margin && dy <= margin) return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if the given player is currently disconnected from the game.
     * A player is considered disconnected if their session is not active.
     *
     * @param game the game to check
     * @param playerId the player ID to check
     * @return true if the player is disconnected, false otherwise
     */
    private boolean isPlayerDisconnected(Game game, UUID playerId) {
        // ⭐ IMPLEMENT THIS based on your session tracking!

        // Option 1: Using PlayerSession repository
        // return playerSessionRepository
        //     .findByGameAndPlayerId(game, playerId)
        //     .map(session -> !session.isActive())
        //     .orElse(true);  // No session = disconnected

        // Option 2: Using connectionCleanupService
        return !connectionCleanupService.isPlayerConnected(game.getGameCode(), playerId.toString());

        // Option 3: Check if player has active WebSocket
        // return !webSocketSessionRegistry.hasActiveSession(playerId);

        // ⭐ TEMPORARY: Always return true to allow resume during disconnect
        // You should implement proper session tracking!
        //return true;  // FIXME: Implement proper disconnect detection!
    }

    @Autowired
    private ConnectionCleanupService connectionCleanupService;

}