package group107.distancealert;

import android.os.SystemClock;
import android.util.Log;

/**
 * Classe ausiliaria che si occupa di gestire lo sleep dei thread
 */
@SuppressWarnings("WeakerAccess")
public final class SleepHelper
{
    /**
     * Stringa utile per log della classe SleepHelper
     */
    private static final String TAG = "SleepHelper";

    /**
     * Ferma il thread, tentando di porlo in sleep, per almeno un numero specifico di millisecondi
     *
     * @param timeout Il numero di millisecondi
     */
    public static void sleepMillis(long timeout)
    {
        long startTime = SystemClock.uptimeMillis();

        // Se lo sleep fallisce, viene fatto busy-waiting
        do {
            // Equivalente a TimeUnit.MILLISECONDS.sleep(), ma ignora automaticamente l'interrupt del thread
            SystemClock.sleep(timeout - (SystemClock.uptimeMillis() - startTime));
        }while((SystemClock.uptimeMillis() - startTime) < timeout);

        long endTime = SystemClock.uptimeMillis();

        Log.v(TAG, "sleepMillis(" + timeout + " ms)=> tempo atteso: " + (endTime - startTime) + " ms.");
    }
}