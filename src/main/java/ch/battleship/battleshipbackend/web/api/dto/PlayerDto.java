package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Player;

import java.util.UUID;

public record PlayerDto(
        UUID id,
        String username
) {
    public static PlayerDto from(Player player) {
        return new PlayerDto(
                player.getId(),
                player.getUsername()
        );
    }
}
