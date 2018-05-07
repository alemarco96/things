package group107.raspberryupdate;

import com.google.android.things.AndroidThings;
import com.google.android.things.device.DeviceManager;
import com.google.android.things.update.UpdateManager;
import com.google.android.things.update.UpdateManagerStatus;

public class UpdateHelper
{
    public static String getOSVersionString()
    {
        return "Android Things SDK " + AndroidThings.SDK_INT + " " + AndroidThings.RELEASE + ".";
    }

    public static String getBuildInfoString()
    {
        return "Build: " + AndroidThings.Product.BUILD_NAME + "  ID: " + AndroidThings.Product.BUILD_ID + "  Timestamp: " + AndroidThings.Product.BUILD_TIMESTAMP;
    }

    public static String getUpdateChannelString()
    {
        UpdateManager manager = UpdateManager.getInstance();
        return "Canale di aggiornamento: " + manager.getChannel();
    }

    public static String getUpdateStatusString(UpdateManagerStatus status)
    {
        switch(status.currentState)
        {
            case UpdateManagerStatus.STATE_IDLE:
            {
                return "Il dispositivo è in stato di idle.";
            }
            case UpdateManagerStatus.STATE_CHECKING_FOR_UPDATES:
            {
                return "Ricerca di nuovi aggiornamenti in corso...";
            }
            case UpdateManagerStatus.STATE_UPDATE_AVAILABLE:
            {
                return "Aggiornamento disponibile: " + status.pendingUpdateInfo.versionInfo.buildId + " Download Size: " + status.pendingUpdateInfo.downloadSize;
            }
            case UpdateManagerStatus.STATE_DOWNLOADING_UPDATE:
            {
                int progress = (int) (status.pendingUpdateInfo.downloadProgress * 1000);
                int intProgress = progress / 10;
                int decProgress = progress % 10;

                return "Download dell'aggiornamento in corso... " + intProgress + "." + decProgress + "%";
            }
            case UpdateManagerStatus.STATE_FINALIZING_UPDATE:
            {
                return "Installazione dell'aggiornamento in corso...";
            }
            case UpdateManagerStatus.STATE_UPDATED_NEEDS_REBOOT:
            {
                return "Aggiornamento installato. Il dispositivo necessita di essere riavviato per ultimare l'aggiornamento.";
            }
            case UpdateManagerStatus.STATE_REPORTING_ERROR:
            {
                return "Errore durante la procedura di aggiornamento.";
            }
            default:
            {
                throw new IllegalArgumentException();
            }
        }
    }

    public static void rebootDevice()
    {
        DeviceManager manager = DeviceManager.getInstance();
        manager.reboot();
    }

    public static void factoryResetDevice()
    {
        DeviceManager manager = DeviceManager.getInstance();
        manager.factoryReset(true);
    }
}