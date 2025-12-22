package ch.battleship.battleshipbackend.domain.enums;

public enum GameEventType
{
    BOARD_CONFIRMED,
    BOARD_REROLLED,
    GAME_STARTED,
    GAME_FINISHED,
    GAME_PAUSED,
    GAME_RESUMED,
    GAME_FORFEITED,
    SHOT_FIRED,
    TURN_CHANGED
}
