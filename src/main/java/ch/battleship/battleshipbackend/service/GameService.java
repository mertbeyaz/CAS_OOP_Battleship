package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;

import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import ch.battleship.battleshipbackend.web.api.dto.ShipPlacementDto;
import ch.battleship.battleshipbackend.web.api.dto.ShotDto;
import ch.battleship.battleshipbackend.web.api.dto.ShotEventDto;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
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


}
