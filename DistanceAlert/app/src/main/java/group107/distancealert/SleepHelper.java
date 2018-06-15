package group107.distancealert;

import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Classe ausiliaria che si occupa di gestire lo sleep dei thread
 */
public final class SleepHelper
{
    /**
     * TAG usato per loggare esclusivamente le eccezioni lanciate in questa classe
     */
    private static final String TAG = "SleepHelper";

    /**
     * Ferma il thread, tentando di porlo in sleep, per almeno un numero specifico di millisecondi
     * @param timeout Il numero di millisecondi
     */
    @SuppressWarnings("EmptyCatchBlock")
    public static void sleepMillis(long timeout)
    {
        long startTime = SystemClock.uptimeMillis();

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            //equivalente a TimeUnit.MILLISECONDS.sleep(), ma ignora automaticamente l'interrupt del thread
            SystemClock.sleep(timeout - (SystemClock.uptimeMillis() - startTime));
        }while((SystemClock.uptimeMillis() - startTime) < timeout);

        long endTime = SystemClock.uptimeMillis();

        Log.v(TAG, "sleepMillis(" + timeout + " ms)=> tempo atteso: " + (endTime - startTime) + " ms.");
    }

    /**
     * Ferma il thread, tentando di porlo in sleep, per almeno un numero specifico di microsecondi
     * @param timeout Il numero di microsecondi
     */
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
                //ignora l'interrupt del thread, e logga l'eccezione lanciata
                Log.e(TAG, "Sleep interrupted", e);
            }
        }while(((SystemClock.elapsedRealtimeNanos() / 1000L) - startTime) < timeout);

        long endTime = SystemClock.elapsedRealtimeNanos() / 1000L;

        Log.v(TAG, "sleepMicros(" + timeout + " us)=> tempo atteso: " + (endTime - startTime) + " us.");
    }

    /**
     * Ferma il thread, tentando di porlo in sleep, per almeno un numero specifico di nanosecondi
     * @param timeout Il numero di nanosecondi
     */
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
                //ignora l'interrupt del thread, e logga l'eccezione lanciata
                Log.e(TAG, "Sleep interrupted", e);
            }
        }while((SystemClock.elapsedRealtimeNanos() - startTime) < timeout);

        long endTime = SystemClock.elapsedRealtimeNanos();

        Log.v(TAG, "sleepNanos(" + timeout + " ns)=> tempo atteso: " + (endTime - startTime) + " ns.");
    }
}