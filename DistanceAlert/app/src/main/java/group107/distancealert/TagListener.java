package group107.distancealert;

import java.io.IOException;

public interface TagListener
{
    void onTagHasConnected(final int tagDistance);
    void onTagHasDisconnected(final int tagLastKnownDistance);
    void onTagDataAvailable(final int tagDistance);

    void onError(final IOException e);
}