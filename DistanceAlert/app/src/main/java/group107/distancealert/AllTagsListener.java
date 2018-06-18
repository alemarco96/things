package group107.distancealert;

import java.util.List;

public interface AllTagsListener
{
    void onTagHasConnected(final List<DistanceController.Entry> tags);

    void onTagHasDisconnected(final List<DistanceController.Entry> tags);

    void onTagDataAvailable(final List<DistanceController.Entry> tags);

    void onError(final String shortDescription, final Exception e);
}
