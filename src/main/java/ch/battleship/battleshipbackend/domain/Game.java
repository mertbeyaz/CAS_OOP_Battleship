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

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Column(nullable = false, unique = true, length = 64)
    private String gameCode;

    @Embedded
    private GameConfiguration config;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id") // FK in players-Tabelle, aber Player kennt Game nicht
    private List<Player> players = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id") // FK in boards-Tabelle, Board kennt Game nicht
    private List<Board> boards = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id")
    private List<Shot> shots = new ArrayList<>();

    public Game(GameConfiguration config) {
        this.status = GameStatus.WAITING;
        this.gameCode = UUID.randomUUID().toString();
        this.config = config;
    }

    public Game(String gameCode, GameConfiguration config) {
        this.status = GameStatus.WAITING;
        this.gameCode = gameCode;
        this.config = config;
    }


    public void addPlayer(Player player) {
        this.players.add(player);
    }

    public void addBoard(Board board) {
        this.boards.add(board);
    }

    public void addShot(Shot shot) {
        this.shots.add(shot);
    }

    public Shot fireShot(Player shooter, Board targetBoard, Coordinate coordinate) {
        // 1) Schon geschossen?
        boolean alreadyShot = shots.stream()
                .filter(s -> s.getTargetBoard().equals(targetBoard))
                .anyMatch(s -> s.getCoordinate().equals(coordinate));

        if (alreadyShot) {
            Shot shot = new Shot(coordinate, ShotResult.ALREADY_SHOT, shooter, targetBoard);
            addShot(shot);
            return shot;
        }

        // 2) Treffer prüfen
        boolean hit = targetBoard.getPlacements().stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .anyMatch(c -> c.equals(coordinate));

        if (!hit) {
            Shot shot = new Shot(coordinate, ShotResult.MISS, shooter, targetBoard);
            addShot(shot);
            return shot;
        }

        // 3) Herausfinden, welches Schiff getroffen wurde
        ShipPlacement hitPlacement = targetBoard.getPlacements().stream()
                .filter(p -> p.getCoveredCoordinates().contains(coordinate))
                .findFirst()
                .orElseThrow(); // sollte nicht passieren, da hit == true

        // 4) Prüfen, ob das Schiff jetzt komplett versenkt ist
        boolean allCoordsOfShipHit = hitPlacement.getCoveredCoordinates().stream()
                .allMatch(shipCoord ->
                        shots.stream()
                                .filter(s -> s.getTargetBoard().equals(targetBoard))
                                .anyMatch(s -> s.getCoordinate().equals(shipCoord))
                                || shipCoord.equals(coordinate) // aktueller Schuss
                );

        ShotResult result = allCoordsOfShipHit ? ShotResult.SUNK : ShotResult.HIT;
        Shot shot = new Shot(coordinate, result, shooter, targetBoard);
        addShot(shot);
        return shot;
    }
}
