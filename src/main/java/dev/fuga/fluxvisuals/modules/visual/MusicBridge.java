package dev.fuga.fluxvisuals.modules.visual;

import by.bonenaut7.mediatransport4j.api.MediaSession;
import by.bonenaut7.mediatransport4j.api.MediaTransport;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class MusicBridge {
    private static final long MAX_REASONABLE_TRACK_MS = 1000L * 60L * 60L * 8L;
    private static final long WAVE_EPOCH_MS = System.currentTimeMillis();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "fluxvisuals-music");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final Object LOCK = new Object();
    private static final float[] WAVE_HEIGHTS = new float[4];
    private static final float[] WAVE_TARGETS = new float[4];

    private static volatile boolean started;
    private static volatile boolean backendAttempted;
    private static volatile boolean backendAvailable;
    private static volatile MediaSession activeSession;
    private static volatile String lastMetadataKey = "";
    private static volatile long lastWaveUpdate;
    private static volatile MusicSnapshot snapshot = MusicSnapshot.empty();

    static {
        start();
    }

    private MusicBridge() {
    }

    static MusicSnapshot snapshot() {
        return snapshot;
    }

    static boolean isBackendAvailable() {
        return backendAvailable;
    }

    static void refreshNow() {
        poll();
    }

    static boolean previous() {
        return invokeControl(MediaSession::switchToPrevious);
    }

    static boolean next() {
        return invokeControl(MediaSession::switchToNext);
    }

    static boolean togglePlay() {
        return invokeControl(MediaSession::togglePlay);
    }

    static boolean canSeek() {
        return false;
    }

    static boolean seekFraction(float fraction) {
        return false;
    }

    static void forceRefresh() {
        lastMetadataKey = "";
        poll();
    }

    private static void start() {
        if (started) {
            return;
        }
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
            EXECUTOR.scheduleAtFixedRate(MusicBridge::poll, 0L, 40L, TimeUnit.MILLISECONDS);
        }
    }

    private static void poll() {
        try {
            if (!backendAttempted) {
                backendAttempted = true;
                backendAvailable = MediaTransport.init();
                if (!backendAvailable) {
                    clearSnapshot();
                    return;
                }
            }

            if (!backendAvailable) {
                clearSnapshot();
                return;
            }

            List<MediaSession> sessions;
            try {
                sessions = MediaTransport.getMediaSessions();
            } catch (Throwable ignored) {
                clearSnapshot();
                return;
            }

            if (sessions == null || sessions.isEmpty()) {
                clearSnapshot();
                return;
            }

            MediaSession session = chooseSession(sessions);
            if (session == null) {
                clearSnapshot();
                return;
            }

            activeSession = session;

            String sourceApp = safeString(session.getSourceApp());
            String title = safeString(session.getTitle());
            String artist = safeString(session.getArtist());
            TimelineValues timeline = normalizeTimeline(session.getDuration(), session.getPosition());
            long durationMs = timeline.durationMs();
            long positionMs = timeline.positionMs();
            positionMs = clampLong(positionMs, 0L, durationMs <= 0L ? positionMs : durationMs);
            boolean playing = safeBoolean(session::isPlaying);
            String metadataKey = sourceApp + "|" + title + "|" + artist + "|" + durationMs;

            MusicSnapshot current = snapshot;
            if (!playing && isTimelineAdvancing(current, metadataKey, durationMs, positionMs)) {
                playing = true;
            }
            byte[] thumbnailBytes = current.present() ? current.thumbnailBytes() : null;
            String thumbnailHash = current.present() ? current.thumbnailHash() : "";

            if (!metadataKey.equals(lastMetadataKey)) {
                byte[] freshThumbnail = readThumbnail(session);
                if (freshThumbnail != null) {
                    thumbnailBytes = freshThumbnail;
                    thumbnailHash = hashBytes(freshThumbnail);
                } else {
                    thumbnailBytes = null;
                    thumbnailHash = "";
                }
                lastMetadataKey = metadataKey;
            } else if ((thumbnailBytes == null || thumbnailBytes.length == 0) && session.hasThumbnail()) {
                byte[] freshThumbnail = readThumbnail(session);
                if (freshThumbnail != null) {
                    thumbnailBytes = freshThumbnail;
                    thumbnailHash = hashBytes(freshThumbnail);
                }
            }

            updateWaveHeights(playing);
            snapshot = new MusicSnapshot(
                    true,
                    title,
                    artist,
                    "",
                    sourceApp,
                    metadataKey,
                    thumbnailHash,
                    thumbnailBytes,
                    durationMs,
                    positionMs,
                    playing,
                    System.currentTimeMillis(),
                    WAVE_HEIGHTS.clone()
            );
        } catch (Throwable ignored) {
            clearSnapshot();
        }
    }

    private static MediaSession chooseSession(List<MediaSession> sessions) {
        MediaSession first = null;
        MediaSession playing = null;
        MediaSession withMetadata = null;

        for (MediaSession session : sessions) {
            if (session == null) {
                continue;
            }
            if (first == null) {
                first = session;
            }

            boolean hasMetadata = hasMetadata(session);
            boolean isPlaying = safeBoolean(session::isPlaying);
            if (isPlaying && hasMetadata) {
                return session;
            }
            if (isPlaying && playing == null) {
                playing = session;
            }
            if (hasMetadata && withMetadata == null) {
                withMetadata = session;
            }
        }

        if (playing != null) {
            return playing;
        }
        if (withMetadata != null) {
            return withMetadata;
        }
        return first;
    }

    private static boolean hasMetadata(MediaSession session) {
        return !safeString(session.getTitle()).isEmpty()
                || !safeString(session.getArtist()).isEmpty()
                || !safeString(session.getSourceApp()).isEmpty();
    }

    private static boolean isTimelineAdvancing(MusicSnapshot current, String metadataKey, long durationMs, long positionMs) {
        if (current == null || !current.present() || !metadataKey.equals(current.metadataKey())) {
            return false;
        }
        if (durationMs <= 0L || current.durationMs() <= 0L) {
            return false;
        }
        if (Math.abs(durationMs - current.durationMs()) > 2_000L) {
            return false;
        }
        long previousPosition = current.currentPositionMs(System.currentTimeMillis());
        return positionMs > previousPosition + 120L;
    }

    private static void updateWaveHeights(boolean playing) {
        long now = System.currentTimeMillis();
        if (playing) {
            double t = (now - WAVE_EPOCH_MS) / 1000.0D;
            lastWaveUpdate = now;
            WAVE_TARGETS[0] = 2.3F + wavePulse(t, 7.10D, 0.20D, 1.10D) * 7.4F;
            WAVE_TARGETS[1] = 2.6F + wavePulse(t, 8.00D, 0.95D, 1.85D) * 7.8F;
            WAVE_TARGETS[2] = 2.1F + wavePulse(t, 6.55D, 1.70D, 2.60D) * 7.0F;
            WAVE_TARGETS[3] = 2.5F + wavePulse(t, 7.65D, 2.45D, 3.35D) * 7.5F;
        } else {
            for (int i = 0; i < WAVE_TARGETS.length; i++) {
                WAVE_TARGETS[i] = 2.2F;
            }
        }

        for (int i = 0; i < WAVE_HEIGHTS.length; i++) {
            float smoothing = playing ? (i % 2 == 0 ? 0.42F : 0.48F) : 0.18F;
            WAVE_HEIGHTS[i] += (WAVE_TARGETS[i] - WAVE_HEIGHTS[i]) * smoothing;
        }
    }

    private static float wavePulse(double time, double speed, double phaseA, double phaseB) {
        double primary = 0.5D + 0.5D * Math.sin(time * speed + phaseA);
        double secondary = 0.5D + 0.5D * Math.sin(time * (speed * 0.58D) + phaseB);
        double mixed = primary * 0.72D + secondary * 0.28D;
        return (float) Math.max(0.0D, Math.min(1.0D, mixed));
    }

    private static byte[] readThumbnail(MediaSession session) {
        try {
            if (!session.hasThumbnail()) {
                return null;
            }
            ByteBuffer buffer = session.getThumbnail();
            if (buffer == null) {
                return null;
            }

            ByteBuffer duplicate = buffer.asReadOnlyBuffer();
            duplicate.clear();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            return bytes.length == 0 ? null : bytes;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeControl(ControlCall call) {
        MediaSession session = activeSession;
        if (session == null) {
            return false;
        }

        try {
            return call.call(session);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clearSnapshot() {
        activeSession = null;
        lastMetadataKey = "";
        updateWaveHeights(false);
        snapshot = MusicSnapshot.empty();
    }

    private static String hashBytes(byte[] bytes) {
        return Integer.toHexString(Arrays.hashCode(bytes));
    }

    private static TimelineValues normalizeTimeline(long rawDuration, long rawPosition) {
        long safeDuration = Math.max(0L, rawDuration);
        long safePosition = Math.max(0L, rawPosition);
        if (safeDuration <= 0L && safePosition <= 0L) {
            return new TimelineValues(0L, 0L);
        }

        TimelineValues[] candidates = new TimelineValues[] {
                new TimelineValues(scaleSeconds(safeDuration), scaleSeconds(safePosition)),
                new TimelineValues(safeDuration, safePosition),
                new TimelineValues(safeDuration / 1_000L, safePosition / 1_000L),
                new TimelineValues(safeDuration / 10_000L, safePosition / 10_000L),
                new TimelineValues(safeDuration / 10_000L, safePosition),
                new TimelineValues(safeDuration / 10_000L, safePosition / 1_000L),
                new TimelineValues(safeDuration / 1_000L, safePosition),
                new TimelineValues(safeDuration, safePosition / 1_000L),
                new TimelineValues(scaleSeconds(safeDuration), safePosition),
                new TimelineValues(scaleSeconds(safeDuration), safePosition / 1_000L)
        };

        TimelineValues best = candidates[1];
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < candidates.length; i++) {
            TimelineValues candidate = candidates[i];
            int score = timelineScore(candidate.durationMs(), candidate.positionMs(), i, safeDuration, safePosition);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return new TimelineValues(best.durationMs(), best.positionMs());
    }

    private static long scaleSeconds(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value > Long.MAX_VALUE / 1_000L ? Long.MAX_VALUE : value * 1_000L;
    }

    private static int timelineScore(long durationMs, long positionMs, int candidateIndex, long rawDuration, long rawPosition) {
        if (durationMs < 0L || positionMs < 0L || durationMs > MAX_REASONABLE_TRACK_MS) {
            return Integer.MIN_VALUE / 4;
        }

        int score = 0;
        if (durationMs == 0L) {
            return positionMs == 0L ? 0 : -500;
        }

        if (positionMs <= durationMs + Math.max(2_000L, durationMs / 8L)) {
            score += 40;
        } else {
            score -= 120;
        }

        if (durationMs >= 90_000L && durationMs <= 900_000L) {
            score += 80;
        } else if (durationMs >= 20_000L && durationMs <= MAX_REASONABLE_TRACK_MS) {
            score += 40;
        } else if (durationMs >= 1_000L) {
            score += 10;
        }

        if (durationMs > 2_700_000L) {
            score -= 12;
        }

        if (candidateIndex == 0) {
            if (rawDuration > 0L && rawDuration < 10_000L && rawPosition < 10_000L) {
                score += 55;
            }
        } else if (candidateIndex == 1) {
            if (rawDuration >= 10_000L && rawDuration <= MAX_REASONABLE_TRACK_MS) {
                score += 25;
            }
        } else if (candidateIndex == 2) {
            if (rawDuration >= 1_000_000L) {
                score += 18;
            }
        } else if (candidateIndex == 3) {
            if (rawDuration >= 10_000_000L) {
                score += 22;
            }
        } else if (candidateIndex == 4 || candidateIndex == 5) {
            if (rawDuration >= 10_000_000L && rawPosition <= MAX_REASONABLE_TRACK_MS) {
                score += 36;
            }
        } else if (candidateIndex == 6 || candidateIndex == 7) {
            if (rawDuration >= 1_000_000L && rawPosition <= MAX_REASONABLE_TRACK_MS) {
                score += 26;
            }
        } else if (candidateIndex == 8 || candidateIndex == 9) {
            if (rawDuration < 10_000L && rawPosition <= MAX_REASONABLE_TRACK_MS) {
                score += 18;
            }
        }

        return score;
    }

    private static boolean safeBoolean(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface ControlCall {
        boolean call(MediaSession session);
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private record TimelineValues(long durationMs, long positionMs) {
    }

    public record MusicSnapshot(
            boolean present,
            String title,
            String artist,
            String album,
            String sourceApp,
            String metadataKey,
            String thumbnailHash,
            byte[] thumbnailBytes,
            long durationMs,
            long positionMs,
            boolean playing,
            long sampleTimeMs,
            float[] waveHeights
    ) {
        public MusicSnapshot {
            title = safe(title);
            artist = safe(artist);
            album = safe(album);
            sourceApp = safe(sourceApp);
            metadataKey = safe(metadataKey);
            thumbnailHash = safe(thumbnailHash);
            thumbnailBytes = thumbnailBytes == null ? null : thumbnailBytes.clone();
            waveHeights = waveHeights == null ? new float[4] : waveHeights.clone();
        }

        static MusicSnapshot empty() {
            return new MusicSnapshot(false, "", "", "", "", "", "", null, 0L, 0L, false, System.currentTimeMillis(), new float[4]);
        }

        public float progressFraction(long now) {
            if (durationMs <= 0L) {
                return 0.0F;
            }
            long position = currentPositionMs(now);
            position = Math.max(0L, Math.min(durationMs, position));
            return Math.max(0.0F, Math.min(1.0F, position / (float) durationMs));
        }

        public long currentPositionMs(long now) {
            long position = positionMs;
            if (playing) {
                position += Math.max(0L, now - sampleTimeMs);
            }
            if (durationMs <= 0L) {
                return Math.max(0L, position);
            }
            return Math.max(0L, Math.min(durationMs, position));
        }

        public boolean hasCover() {
            return thumbnailBytes != null && thumbnailBytes.length > 0;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
