package ch.battleship.battleshipbackend.domain.enums;

/**
 * Represents the outcome of a shot fired at a board coordinate.
 */
public enum ShotResult {
    /**
     * No ship was hit.
     */
    MISS,

    /**
     * A ship was hit but not sunk.
     */
    HIT,

    /**
     * A ship was hit and sunk.
     */
    SUNK,

    /**
     * The targeted coordinate was already shot before and therefore no new shot was applied.
     */
    ALREADY_SHOT
}
