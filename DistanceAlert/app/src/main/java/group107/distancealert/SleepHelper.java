package group107.distancealert;

import android.util.Log;

import java.util.concurrent.TimeUnit;

public final class SleepHelper
{
    private static final String TAG = "SleepHelper";

    @SuppressWarnings("EmptyCatchBlock")
    public static void sleepMillis(long timeout)
    {
        long startTime = System.currentTimeMillis();

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            try
            {
                TimeUnit.MILLISECONDS.sleep(timeout - (System.currentTimeMillis() - startTime));
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }
        }while((System.currentTimeMillis() - startTime) < timeout);
    }

    @SuppressWarnings("EmptyCatchBlock")
    public static void sleepMicros(long timeout)
    {
        long startTime = System.nanoTime() / 1000L;

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            try
            {
                TimeUnit.MICROSECONDS.sleep(timeout - ((System.currentTimeMillis() / 1000L) - startTime));
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }
        }while(((System.nanoTime() / 1000L) - startTime) < timeout);
    }

    @SuppressWarnings({"unused", "EmptyCatchBlock"})
    public static void sleepNanos(long timeout)
    {
        long startTime = System.nanoTime();

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            try
            {
                TimeUnit.NANOSECONDS.sleep(timeout - (System.currentTimeMillis() - startTime));
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }
        }while((System.nanoTime() - startTime) < timeout);
    }
}