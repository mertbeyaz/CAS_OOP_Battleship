package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.repository.GameRepository;

import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.web.api.dto.*;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
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

    // Konstruktor für Spring (mit WebSocket)
    @Autowired
    public GameService(GameRepository gameRepository, SimpMessagingTemplate messagingTemplate, ShotRepository shotRepository) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
        this.shotRepository = shotRepository;
    }

    // Zusätzlicher Konstruktor für Tests, die nur den Repo-Mock haben
    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = null; // in Tests kein WebSocket nötig
        this.shotRepository = null;
    }

    public Game createNewGame() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);
        return gameRepository.save(game);
    }

    public Optional<Game> getByGameCode(String gameCode) {
        return gameRepository.findByGameCode(gameCode);
    }

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

        Player player = new Player(username);
        game.addPlayer(player);

        Board board = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                player
        );

        autoPlaceFleet(board, game.getConfig());

        game.addBoard(board);

        // When the second player joins, we switch to SETUP.
        // Boards can still be re-rolled/confirmed. Shooting is only allowed in RUNNING.
        if (currentPlayers + 1 == 2) {
            game.setStatus(GameStatus.SETUP);
        }

        return gameRepository.save(game);
    }

    public Shot fireShot(String gameCode, UUID shooterId, UUID targetBoardId, int x, int y) {
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
                .filter(b -> Objects.equals(b.getId(), targetBoardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        if (Objects.equals(targetBoard.getOwner().getId(), shooterId)) {
            throw new IllegalStateException("Player cannot shoot at own board");
        }

        if (x < 0 || x >= targetBoard.getWidth() || y < 0 || y >= targetBoard.getHeight()) {
            throw new IllegalArgumentException("Shot coordinate out of board bounds");
        }

        Coordinate coordinate = new Coordinate(x, y);
        Shot shot = game.fireShot(shooter, targetBoard, coordinate);

        Shot persistedShot = shotRepository.save(shot);

        // TURN LOGIC:
        // - ALREADY_SHOT: Turn bleibt (sonst könnte man Turn verlieren durch Fehlklick)
        // - HIT/SUNK: Turn bleibt
        // - MISS: Turn wechseln
        if (shot.getResult() == ShotResult.MISS) {
            UUID nextTurn = game.getPlayers().stream()
                    .filter(p -> !Objects.equals(p.getId(), shooterId))
                    .map(Player::getId)
                    .findFirst()
                    .orElse(shooterId);
            game.setCurrentTurnPlayerId(nextTurn);
        }

        // WIN CHECK: alle Schiffe des Defenders versenkt?
        boolean defenderAllSunk = targetBoard.getPlacements().stream()
                .allMatch(p -> p.getCoveredCoordinates().stream().allMatch(shipCoord ->
                        game.getShots().stream()
                                .filter(s -> s.getTargetBoard().equals(targetBoard))
                                .anyMatch(s -> s.getCoordinate().equals(shipCoord))
                                || shipCoord.equals(coordinate) // aktueller Schuss
                ));

        if (defenderAllSunk) {
            game.setStatus(GameStatus.FINISHED);
            game.setWinnerPlayerId(shooter.getId());
        }

        Game savedGame = gameRepository.save(game);

        // EVENTS
        if (messagingTemplate != null) {
            String destination = "/topic/games/" + gameCode + "/events";
            Player defender = targetBoard.getOwner();

            // SHOT_FIRED
            messagingTemplate.convertAndSend(destination,
                    GameEventDto.shotFired(savedGame, shooter, defender, x, y, shot.getResult())
            );

            // Wenn finished -> GAME_FINISHED, sonst TURN_CHANGED
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

    public BoardStateDto getBoardState(String gameCode, UUID boardId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getId(), boardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        var ships = board.getPlacements().stream()
                .map(ShipPlacementDto::from)
                .toList();

        var shotsOnThisBoard = game.getShots().stream()
                .filter(s -> s.getTargetBoard().equals(board))
                .map(s -> Map.<String, Object>of(
                        "x", s.getCoordinate().getX(),
                        "y", s.getCoordinate().getY(),
                        "result", s.getResult().name()
                ))
                .toList();

        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.getOwner().getId(),
                board.getOwner().getUsername(),
                board.isLocked(),
                ships,
                shotsOnThisBoard
        );
    }
    /**
     * Re-roll (auto place) the fleet for the given player's board.
     * Allowed only in SETUP and only if the board is not locked yet.
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

        // reset placements and generate again
        board.clearPlacements();
        autoPlaceFleet(board, game.getConfig());

        gameRepository.save(game);

        return getBoardState(gameCode, board.getId());
    }

    /**
     * Confirm (lock) the given player's board. When both boards are locked, the game becomes RUNNING.
     */
    public Game confirmBoard(String gameCode, UUID playerId) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.SETUP) {
            throw new IllegalStateException("Cannot confirm board when game is not in SETUP");
        }

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getOwner().getId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player does not have a board in this game"));

        // basic validation: must have placements for all ships as per fleet definition
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

            // FIRST TURN: only set once
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

            Player player = saved.getPlayers().stream()
                    .filter(p -> Objects.equals(p.getId(), playerId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Player not found in this game"));

            // BOARD_CONFIRMED event
            messagingTemplate.convertAndSend(destination, GameEventDto.boardConfirmed(saved, player));

            // GAME_STARTED event when both boards are locked
            if (allLocked) {
                Player firstTurn = saved.getPlayers().stream()
                        .filter(p -> Objects.equals(p.getId(), saved.getCurrentTurnPlayerId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Current turn player not found"));

                messagingTemplate.convertAndSend(destination, GameEventDto.gameStarted(saved, firstTurn));
            }
        }

        return saved;
    }



    public String getBoardAscii(String gameCode, UUID boardId, boolean showShips) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        Board board = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getId(), boardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        // Shots nur auf dieses Board
        List<Shot> shotsOnThisBoard = game.getShots().stream()
                .filter(s -> s.getTargetBoard().equals(board))
                .toList();

        return renderBoardAscii(board, shotsOnThisBoard, showShips);
    }

    @Transactional
    public Game createGameAndJoinFirstPlayer(String username) {
        GameConfiguration config = GameConfiguration.defaultConfig();

        // Neues Game anlegen (Code + WAITING im Konstruktor)
        Game game = new Game(config);
        game = gameRepository.save(game); // ID & Persistence

        // Erster Spieler joined über die gleiche Logik wie später Spieler 2
        return joinGame(game.getGameCode(), username);
    }

    private List<ShipType> parseFleetDefinition(GameConfiguration config) {
        String definition = config.getFleetDefinition();
        List<ShipType> result = new ArrayList<>();

        if (definition == null || definition.isBlank()) {
            return result; // leere Flotte = kein Schiff
        }

        String[] parts = definition.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

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

            for (int i = 0; i < count; i++) {
                result.add(type);
            }
        }

        return result;
    }

    private String renderBoardAscii(Board board,
                                    List<Shot> shotsOnThisBoard,
                                    boolean showShips) {

        int width = board.getWidth();
        int height = board.getHeight();

        char[][] grid = new char[height][width];

        // Alles mit '.' initialisieren
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = '.';
            }
        }

        // 1) Schiffe einzeichnen (optional)
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

        // 2) Shots einzeichnen: X = Hit/Sunk, O = Miss
        for (Shot shot : shotsOnThisBoard) {
            Coordinate c = shot.getCoordinate();
            int x = c.getX();
            int y = c.getY();

            if (x < 0 || x >= width || y < 0 || y >= height) {
                continue; // safety
            }

            ShotResult result = shot.getResult();

            switch (result) {
                case MISS -> grid[y][x] = 'O';
                case HIT, SUNK -> grid[y][x] = 'X';
                case ALREADY_SHOT -> {
                    // nichts tun: Feld ist schon X/O
                }
            }
        }

        // 3) String bauen (mit Koordinaten)
        StringBuilder sb = new StringBuilder();
        sb.append("Board (").append(width).append("x").append(height).append(")\n");

        // Kopfzeile (X-Achse)
        sb.append("   ");
        for (int x = 0; x < width; x++) {
            sb.append(x).append(' ');
        }
        sb.append('\n');

        // Zeilen (Y-Achse + Inhalt)
        for (int y = 0; y < height; y++) {
            sb.append(String.format("%2d ", y));
            for (int x = 0; x < width; x++) {
                sb.append(grid[y][x]).append(' ');
            }
            sb.append('\n');
        }

        return sb.toString();
    }


    // ------------------------------------------------------------------------------------
    // Randomisierte Flottenplatzierung inklusive Abstand.
    // ------------------------------------------------------------------------------------
    private void autoPlaceFleet(Board board, GameConfiguration config) {

        int margin = config.getShipMargin();

        List<ShipType> fleetTypes = parseFleetDefinition(config);

        List<Ship> fleet = new ArrayList<>();
        for (ShipType type : fleetTypes) {
            fleet.add(new Ship(type));
        }

        // Grösste Schiffe zuerst
        fleet.sort((a, b) ->
                Integer.compare(b.getType().getSize(), a.getType().getSize()));

        List<ShipPlacement> placements = new ArrayList<>();

        Random random = new Random(); // <— hier einmal erzeugen

        boolean success = placeFleetBacktracking(board, fleet, 0, margin, placements, random);

        if (!success) {
            throw new IllegalStateException(String.format(
                    "Could not place fleet on board %dx%d with margin=%d. " +
                            "Check fleet definition vs board size.",
                    board.getWidth(), board.getHeight(), margin
            ));
        }

        // Gefundene Placements aufs Board übernehmen
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
        // Alle Schiffe platziert?
        if (index >= fleet.size()) {
            return true;
        }

        Ship ship = fleet.get(index);

        // Orientations zufällig anordnen
        List<Orientation> orientations = new ArrayList<>(List.of(Orientation.values()));
        java.util.Collections.shuffle(orientations, random);

        // Alle möglichen Positionen sammeln und randomisieren
        List<Coordinate> candidates = new ArrayList<>();
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                candidates.add(new Coordinate(x, y));
            }
        }
        java.util.Collections.shuffle(candidates, random);

        for (Orientation orientation : orientations) {
            for (Coordinate start : candidates) {
                if (canPlaceShipWithMarginVirtual(board, ship, start, orientation, margin, currentPlacements)) {

                    ShipPlacement placement = new ShipPlacement(ship, start, orientation);
                    currentPlacements.add(placement);

                    if (placeFleetBacktracking(board, fleet, index + 1, margin, currentPlacements, random)) {
                        return true;
                    }

                    // Backtracking
                    currentPlacements.remove(currentPlacements.size() - 1);
                }
            }
        }

        // Keine Position/Orientation gefunden --> Backtracking-Ebene failt
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

        // 1) Bounds check
        for (Coordinate c : newCoords) {
            if (c.getX() < 0 || c.getX() >= width ||
                    c.getY() < 0 || c.getY() >= height) {
                return false;
            }
        }

        // 2) Existierende Koordinaten sammeln
        List<Coordinate> existingCoords = currentPlacements.stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .toList();

        // 3) Keine Überlappung
        for (Coordinate nc : newCoords) {
            for (Coordinate ec : existingCoords) {
                if (nc.getX() == ec.getX() && nc.getY() == ec.getY()) {
                    return false;
                }
            }
        }

        // 4) Margin (Chebyshev-Abstand)
        if (margin > 0) {
            for (Coordinate nc : newCoords) {
                for (Coordinate ec : existingCoords) {
                    int dx = Math.abs(nc.getX() - ec.getX());
                    int dy = Math.abs(nc.getY() - ec.getY());
                    if (dx <= margin && dy <= margin) {
                        // Zu nah dran
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
