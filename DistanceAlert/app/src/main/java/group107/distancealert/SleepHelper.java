package group107.distancealert;

import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public final class SleepHelper
{
    private static final String TAG = "SleepHelper";

    @SuppressWarnings("EmptyCatchBlock")
    public static void sleepMillis(long timeout)
    {
        long startTime = SystemClock.uptimeMillis();

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            SystemClock.sleep(timeout - (SystemClock.uptimeMillis() - startTime));
        }while((SystemClock.uptimeMillis() - startTime) < timeout);
    }

    @SuppressWarnings("EmptyCatchBlock")
    public static void sleepMicros(long timeout)
    {
        long startTime = SystemClock.elapsedRealtimeNanos() / 1000L;

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            try
            {
                TimeUnit.MICROSECONDS.sleep(timeout - ((SystemClock.elapsedRealtimeNanos() / 1000L) - startTime));
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted", e);
            }
        }while(((SystemClock.elapsedRealtimeNanos() / 1000L) - startTime) < timeout);
    }

    @SuppressWarnings({"unused", "EmptyCatchBlock"})
    public static void sleepNanos(long timeout)
    {
        long startTime = SystemClock.elapsedRealtimeNanos();

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            try
            {
                TimeUnit.NANOSECONDS.sleep(timeout - (SystemClock.elapsedRealtimeNanos() - startTime));
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted", e);
            }
        }while((SystemClock.elapsedRealtimeNanos() - startTime) < timeout);
    }
}