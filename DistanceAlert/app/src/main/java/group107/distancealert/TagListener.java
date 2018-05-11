package group107.distancealert;

public interface TagListener
{
    void onTagHasConnected(final int tagDistance);
    void onTagHasDisconnected(final int tagLastKnownDistance);
    void onTagDataAvailable(final int tagDistance);
}