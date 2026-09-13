package dev.fuga.fluxvisuals.modules.visual;

public final class AuctionAccessGuard {
    private static final long ANARCHY_JOIN_BLOCK_MS = 11_000L;
    private static long blockedUntilMs;

    private AuctionAccessGuard() {
    }

    public static synchronized long blockAfterAnarchyJoin(long now) {
        long requestedUntil = now > Long.MAX_VALUE - ANARCHY_JOIN_BLOCK_MS
                ? Long.MAX_VALUE
                : now + ANARCHY_JOIN_BLOCK_MS;
        blockedUntilMs = Math.max(blockedUntilMs, requestedUntil);
        return blockedUntilMs;
    }

    public static synchronized boolean isBlocked(long now) {
        return now < blockedUntilMs;
    }

    public static synchronized long readyAt() {
        return blockedUntilMs;
    }
}
