package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.web.api.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ShotRepository shotRepository;

    public GameService(GameRepository gameRepository,
                       SimpMessagingTemplate messagingTemplate,
                       ShotRepository shotRepository) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
        this.shotRepository = shotRepository;
    }

    public Game createNewGame() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);
        return gameRepository.save(game);
    }

    public Optional<Game> getByGameCode(String gameCode) {
        return gameRepository.findByGameCode(gameCode);
    }

    public GamePublicDto getPublicState(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));
        return toPublicDto(game, playerId);
    }

    /**
     * Internal join logic (kept as-is). Controller should usually use joinGamePublic.
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

        // (optional but recommended) prevent duplicate usernames in same game
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

        // when second player joins -> SETUP
        if (currentPlayers + 1 == 2) {
            game.setStatus(GameStatus.SETUP);
        }

        return gameRepository.save(game);
    }

    /**
     * Public join response: returns playerId (required for further requests) + status.
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

    public GameResumeResponseDto resumeGame(String gameCode, UUID requestedByPlayerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        Player requestedBy = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), requestedByPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not belong to this game"));

        if (game.getStatus() == GameStatus.RUNNING) {
            return new GameResumeResponseDto(
                    game.getGameCode(),
                    game.getStatus(),
                    true,
                    requestedBy.getUsername(),
                    toSnapshot(game, requestedByPlayerId)
            );
        }

        if (game.getStatus() == GameStatus.WAITING && game.getResumeReadyPlayerId() == null) {
            throw new IllegalStateException("WAITING game cannot be resumed (not in resume-handshake)");
        }

        if (game.getStatus() != GameStatus.PAUSED && game.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("Can only resume a PAUSED/WAITING game");
        }

        UUID first = game.getResumeReadyPlayerId();

        Game saved;
        if (game.getStatus() == GameStatus.PAUSED) {
            game.setStatus(GameStatus.WAITING);
            game.setResumeReadyPlayerId(requestedByPlayerId);
            saved = gameRepository.save(game);
            sendEventWaiting(saved, requestedBy);
        } else {
            if (Objects.equals(first, requestedByPlayerId)) {
                saved = game; // idempotent
            } else {
                game.setStatus(GameStatus.RUNNING);
                game.setResumeReadyPlayerId(null);
                saved = gameRepository.save(game);
                sendEventResumed(saved, requestedBy);
            }
        }

        boolean handshakeComplete = saved.getStatus() == GameStatus.RUNNING;

        return new GameResumeResponseDto(
                saved.getGameCode(),
                saved.getStatus(),
                handshakeComplete,
                requestedBy.getUsername(),
                toSnapshot(saved, requestedByPlayerId)
        );
    }


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

        // TURN LOGIC:
        if (shot.getResult() == ShotResult.MISS) {
            UUID nextTurn = game.getPlayers().stream()
                    .filter(p -> !Objects.equals(p.getId(), shooterId))
                    .map(Player::getId)
                    .findFirst()
                    .orElse(shooterId);
            game.setCurrentTurnPlayerId(nextTurn);
        }

        // WIN CHECK
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

        // DTO directly (not via dev-only method)
        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.isLocked(),
                board.getPlacements().stream().map(ShipPlacementDto::from).toList()
        );
    }

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

            // BOARD_CONFIRMED
            messagingTemplate.convertAndSend(destination, GameEventDto.boardConfirmed(saved, confirmingPlayer));

            // GAME_STARTED (inkl. currentTurnPlayerName)
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


    @Transactional
    public Game createGameAndJoinFirstPlayer(String username) {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);
        game = gameRepository.save(game);
        return joinGame(game.getGameCode(), username);
    }

    /*
        DEV-only endpoints use these methods via GameDevController (@Profile dev,test).
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

    // ----------------- helpers -----------------
    private void sendEventWaiting(Game game, Player requestedBy) {
        if (messagingTemplate == null) return;
        messagingTemplate.convertAndSend("/topic/games/" + game.getGameCode() + "/events",
                GameEventDto.gameResumePending(game, requestedBy));
    }

    private void sendEventResumed(Game game, Player requestedBy) {
        if (messagingTemplate == null) return;
        messagingTemplate.convertAndSend("/topic/games/" + game.getGameCode() + "/events",
                GameEventDto.gameResumed(game, requestedBy));
    }


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

    private void autoPlaceFleet(Board board, GameConfiguration config) {
        int margin = config.getShipMargin();
        List<ShipType> fleetTypes = parseFleetDefinition(config);

        List<Ship> fleet = new ArrayList<>();
        for (ShipType type : fleetTypes) fleet.add(new Ship(type));

        fleet.sort((a, b) -> Integer.compare(b.getType().getSize(), a.getType().getSize()));

        List<ShipPlacement> placements = new ArrayList<>();
        Random random = new Random();

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

    private GamePublicDto toPublicDto(Game game, UUID requesterPlayerId) {
        Player requester = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> Objects.equals(p.getId(), requesterPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not found in this game: " + requesterPlayerId));

        Player opponent = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> !Objects.equals(p.getId(), requesterPlayerId))
                .findFirst()
                .orElse(null);

        Map<UUID, Board> boardByOwnerId = new HashMap<>();
        for (Board b : game.getBoards()) {
            if (b == null || b.getOwner() == null || b.getOwner().getId() == null) continue;
            boardByOwnerId.put(b.getOwner().getId(), b);
        }

        Board yourBoard = boardByOwnerId.get(requesterPlayerId);
        Board oppBoard = (opponent == null) ? null : boardByOwnerId.get(opponent.getId());

        boolean yourBoardLocked = yourBoard != null && yourBoard.isLocked();
        boolean opponentBoardLocked = oppBoard != null && oppBoard.isLocked();

        boolean yourTurn = game.getStatus() == GameStatus.RUNNING
                && game.getCurrentTurnPlayerId() != null
                && Objects.equals(game.getCurrentTurnPlayerId(), requesterPlayerId);

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

    private GameSnapshotDto toSnapshot(Game game, UUID viewerPlayerId) {
        Player you = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> Objects.equals(p.getId(), viewerPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not found in this game: " + viewerPlayerId));

        Player opponent = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> !Objects.equals(p.getId(), viewerPlayerId))
                .findFirst()
                .orElse(null);

        Map<UUID, Board> boardByOwnerId = new HashMap<>();
        for (Board b : game.getBoards()) {
            if (b == null || b.getOwner() == null || b.getOwner().getId() == null) continue;
            boardByOwnerId.put(b.getOwner().getId(), b);
        }

        Board yourBoard = boardByOwnerId.get(viewerPlayerId);
        Board oppBoard = (opponent == null) ? null : boardByOwnerId.get(opponent.getId());

        boolean yourBoardLocked = yourBoard != null && yourBoard.isLocked();
        boolean oppBoardLocked = oppBoard != null && oppBoard.isLocked();

        boolean yourTurn = game.getStatus() == GameStatus.RUNNING
                && game.getCurrentTurnPlayerId() != null
                && Objects.equals(game.getCurrentTurnPlayerId(), viewerPlayerId);

        List<Shot> allShots = game.getShots() == null ? List.of() : game.getShots();

        BoardStateDto yourBoardDto = (yourBoard == null)
                ? null
                : new BoardStateDto(
                yourBoard.getId(),
                yourBoard.getWidth(),
                yourBoard.getHeight(),
                yourBoard.isLocked(),
                yourBoard.getPlacements().stream().map(ShipPlacementDto::from).toList()
        );

        List<ShotViewDto> shotsOnYourBoard = (yourBoard == null)
                ? List.of()
                : allShots.stream()
                .filter(s -> s.getTargetBoard() != null && s.getTargetBoard().equals(yourBoard))
                .map(ShotViewDto::from)
                .toList();

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
}
