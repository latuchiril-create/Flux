package by.bonenaut7.mediatransport4j.api;

import by.bonenaut7.mediatransport4j.SharedLibraryLoader;
import by.bonenaut7.mediatransport4j.impl.windows.WindowsMediaTransport;
import java.util.List;

public final class MediaTransport {
    private static final SharedLibraryLoader LIBRARY_LOADER = new SharedLibraryLoader();
    private static MediaTransport instance;
    private final MediaTransportInterface transportInterface;

    private MediaTransport() {
        if (SharedLibraryLoader.isWindows) {
            LIBRARY_LOADER.load("mediatransport4j");
            this.transportInterface = new WindowsMediaTransport();
        } else {
            this.transportInterface = null;
        }
    }

    public static final boolean init() {
        if (instance == null) {
            instance = new MediaTransport();
            return instance.transportInterface != null;
        } else {
            return false;
        }
    }

    public static final List<MediaSession> getMediaSessions() {
        return instance.transportInterface.parseSessions();
    }
}
