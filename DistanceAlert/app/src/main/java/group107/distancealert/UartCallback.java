package group107.distancealert;

public interface UartCallback
{
    void onDataAvailable(int[] bytes);
    void onTimeoutCallback();
}
