package ch.battleship.battleshipbackend.domain.enums;

public enum GameStatus {
    WAITING,
    /**
     * Setup phase: both players are present, but boards can still be (re)arranged.
     * Shooting is NOT allowed.
     */
    SETUP,
    READY,
    RUNNING,
    PAUSED,
    FINISHED
}
