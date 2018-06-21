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
     * TAG usato per loggare esclusivamente le eccezioni lanciate in questa classe
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

        //se lo sleep fallisce, viene fatto busy-waiting
        do {
            //equivalente a TimeUnit.MILLISECONDS.sleep(), ma ignora automaticamente l'interrupt del thread
            SystemClock.sleep(timeout - (SystemClock.uptimeMillis() - startTime));
        }while((SystemClock.uptimeMillis() - startTime) < timeout);

        long endTime = SystemClock.uptimeMillis();

        Log.v(TAG, "sleepMillis(" + timeout + " ms)=> tempo atteso: " + (endTime - startTime) + " ms.");
    }
}