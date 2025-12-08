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


@Entity
@Table(name = "lobbies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lobby extends BaseEntity {


    @Column(nullable = false, unique = true, updatable = false)
    private String lobbyCode;   // z.B. auch fürs Frontend, ähnlich wie gameCode

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LobbyStatus status;

    @OneToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false, unique = true)
    private Game game;

    @Version
    private long version; // für Optimistic Locking (gegen Race Conditions)

    public Lobby (Game game) {
        this.lobbyCode = UUID.randomUUID().toString();
        this.status = LobbyStatus.WAITING;
        this.game = Objects.requireNonNull(game);
    }

    public Lobby(String lobbyCode, Game game) {
        this.lobbyCode = Objects.requireNonNull(lobbyCode);
        this.status = LobbyStatus.WAITING;
        this.game = Objects.requireNonNull(game);;
    }

}
