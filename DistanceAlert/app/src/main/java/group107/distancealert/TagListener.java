package group107.distancealert;

public interface TagListener
{
    void onTagHasConnected(int tagDistance);
    void onTagHasDisconnected(int tagLastKnownDistance);
    void onTagDataAvailable(int tagDistance);
}