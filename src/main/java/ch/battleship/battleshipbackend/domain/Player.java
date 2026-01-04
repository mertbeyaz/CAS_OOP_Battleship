package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a player participating in a game.
 *
 * <p>The player currently contains a username used for display and identification within the game.
 * Additional attributes (e.g. email) can be added as an extension if needed.
 */
@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player extends BaseEntity {

    /**
     * Display name chosen by the player.
     */
    @Column(nullable = false, length = 50)
    private String username;

    /**
     * Creates a new player with the given username.
     *
     * @param username display name of the player
     */
    public Player(String username) {
        this.username = username;
    }
}
