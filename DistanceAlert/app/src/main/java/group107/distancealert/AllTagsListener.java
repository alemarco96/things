package group107.distancealert;

import java.util.List;

public interface AllTagsListener
{
    void onTagHasConnected(List<DistanceController.Entry> tags);
    void onTagHasDisconnected(List<DistanceController.Entry> tags);
    void onTagDataAvailable(List<DistanceController.Entry> tags);
}
