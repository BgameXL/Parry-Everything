package dev.bgame.parry;

final class ParryState {
    static final int ACTIVE_TICKS = 4;
    private static final int PERFECT_TICKS = 2;
    private static final int MISS_RECOVERY_TICKS = 12;
    private static final int SUCCESS_RECOVERY_TICKS = 6;

    private long activeUntil;
    private long readyAt;

    void tryStart(long tick) {
        if (tick >= readyAt) {
            activeUntil = tick + ACTIVE_TICKS;
            readyAt = activeUntil + MISS_RECOVERY_TICKS;
        }
    }

    Result consume(long tick) {
        long ticksRemaining = activeUntil - tick;
        if (ticksRemaining <= 0) {
            return Result.NONE;
        }

        activeUntil = 0;
        readyAt = tick + SUCCESS_RECOVERY_TICKS;
        return ticksRemaining > ACTIVE_TICKS - PERFECT_TICKS ? Result.PERFECT : Result.NORMAL;
    }

    enum Result {
        NONE,
        NORMAL,
        PERFECT
    }
}