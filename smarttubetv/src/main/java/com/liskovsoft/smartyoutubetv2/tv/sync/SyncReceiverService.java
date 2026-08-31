package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Servicio Android que mantiene vivo el SyncWebSocketServer mientras la pantalla
 * está lista para recibir comandos de sincronización desde el teléfono.
 */
public class SyncReceiverService extends Service {
    private static final String TAG = SyncReceiverService.class.getSimpleName();
    private static final int SYNC_PORT = 8765;

    private SyncWebSocketServer mServer;

    @Override
    public void onCreate() {
        super.onCreate();

        SyncPlayerBridge bridge = new SyncPlaybackBridge(getApplicationContext());
        mServer = new SyncWebSocketServer(SYNC_PORT, bridge);

        try {
            mServer.start();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar el servidor de sincronizacion", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: si Android mata el servicio, intenta recrearlo.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mServer != null) {
            try {
                mServer.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error al detener el servidor de sincronizacion", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
