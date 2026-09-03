package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Servicio Android que mantiene vivo el SyncWebSocketServer mientras la pantalla
 * está lista para recibir comandos de sincronización desde el teléfono.
 *
 * YG Sync diagnostic: se agregaron Toasts visibles en el arranque y en
 * cualquier fallo, porque previamente un error de arranque quedaba
 * completamente en silencio (ver comentario en SyncWebSocketServer.onError).
 */
public class SyncReceiverService extends Service {
    private static final String TAG = SyncReceiverService.class.getSimpleName();
    private static final int SYNC_PORT = 8765;

    private SyncWebSocketServer mServer;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "YG Sync: iniciando servicio");
        showDiagnostic("YG SYNC — INICIANDO SERVICIO");

        SyncPlayerBridge bridge = new SyncPlaybackBridge(getApplicationContext());
        mServer = new SyncWebSocketServer(SYNC_PORT, bridge, getApplicationContext());

        try {
            mServer.start();
            Log.d(TAG, "YG Sync: WebSocket iniciado en TCP " + SYNC_PORT);
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: ERROR iniciando WebSocket", e);
            showDiagnostic("YG SYNC — ERROR AL INICIAR: " + e.getMessage());
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

    private void showDiagnostic(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "YG Sync diagnostic display error: " + e.getMessage());
            }
        });
    }
}
