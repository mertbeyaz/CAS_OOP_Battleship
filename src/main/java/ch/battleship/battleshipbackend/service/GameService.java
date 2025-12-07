package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.repository.GameRepository;

import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import ch.battleship.battleshipbackend.web.api.dto.ShipPlacementDto;
import ch.battleship.battleshipbackend.web.api.dto.ShotDto;
import ch.battleship.battleshipbackend.web.api.dto.ShotEventDto;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Konstruktor für Spring (mit WebSocket)
    @Autowired
    public GameService(GameRepository gameRepository, SimpMessagingTemplate messagingTemplate) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Zusätzlicher Konstruktor für Tests, die nur den Repo-Mock haben
    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = null; // in Tests kein WebSocket nötig
    }

    public Game createNewGame() {
        String gameCode = UUID.randomUUID().toString();
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(gameCode, config);
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

        // Flotte auf dem Board platzieren
        autoPlaceFleet(board, game.getConfig());

        game.addBoard(board);

        if (currentPlayers + 1 == 2) {
            game.setStatus(GameStatus.RUNNING);
        }

        return gameRepository.save(game);
    }

    public Shot fireShot(String gameCode, UUID shooterId, UUID targetBoardId, int x, int y) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        if (game.getStatus() != GameStatus.RUNNING) {
            throw new IllegalStateException("Cannot fire shot when game is not RUNNING");
        }

        Player shooter = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), shooterId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Shooter does not belong to this game"));

        Board targetBoard = game.getBoards().stream()
                .filter(b -> Objects.equals(b.getId(), targetBoardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Board does not belong to this game"));

        // Optional: verhindern, dass man auf das eigene Board schießt
        if (Objects.equals(targetBoard.getOwner().getId(), shooterId)) {
            throw new IllegalStateException("Player cannot shoot at own board");
        }

        // Bounds Check
        if (x < 0 || x >= targetBoard.getWidth() || y < 0 || y >= targetBoard.getHeight()) {
            throw new IllegalArgumentException("Shot coordinate out of board bounds");
        }

        Coordinate coordinate = new Coordinate(x, y);
        Shot shot = game.fireShot(shooter, targetBoard, coordinate);

        gameRepository.save(game); // Shots werden per Cascade mitgespeichert

        // --- WebSocket Event bauen & senden ---
        if (messagingTemplate != null) { // in Unit-Tests kann das null sein
            String attackerName = shooter.getUsername();
            String defenderName = targetBoard.getOwner().getUsername();

            // simple Koordinaten-Repräsentation, z.B. "3,5"
            String coordinateStr = x + "," + y;

            boolean hit = shot.getResult() == ShotResult.HIT || shot.getResult() == ShotResult.SUNK;
            boolean shipSunk = shot.getResult() == ShotResult.SUNK;

            // "nächster Spieler" = der andere im 2-Spieler-Game
            String nextPlayerName = game.getPlayers().stream()
                    .filter(p -> !Objects.equals(p.getId(), shooterId))
                    .map(Player::getUsername)
                    .findFirst()
                    .orElse(attackerName); // Fallback, falls nur 1 Spieler

            ShotEventDto event = new ShotEventDto(
                    gameCode,
                    attackerName,
                    defenderName,
                    coordinateStr,
                    hit,
                    shipSunk,
                    game.getStatus(),
                    nextPlayerName
            );

            String destination = "/topic/games/" + gameCode;
            messagingTemplate.convertAndSend(destination, event);
        }

        return shot;
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
                .map(ShotDto::from)
                .toList();

        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.getOwner().getId(),
                board.getOwner().getUsername(),
                ships,
                shotsOnThisBoard
        );
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
