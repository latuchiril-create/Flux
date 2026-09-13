package by.bonenaut7.mediatransport4j.api;

import java.nio.ByteBuffer;

public interface MediaSession {
    String getSourceApp();

    boolean switchToNext();

    boolean switchToPrevious();

    String getArtist();

    String getTitle();

    boolean hasThumbnail();

    ByteBuffer getThumbnail();

    long getDuration();

    long getPosition();

    boolean isPlaying();

    boolean play();

    boolean pause();

    boolean togglePlay();

    boolean stop();
}
