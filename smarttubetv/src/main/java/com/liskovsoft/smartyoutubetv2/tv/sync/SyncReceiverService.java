package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

/**
 * Servicio foreground que mantiene vivo el SyncWebSocketServer
 * mientras SmartTube está funcionando.
 *
 * YG Sync:
 * TCP 8765 = WebSocket de control
 *
 * El servicio se ejecuta como foreground para evitar que Android
 * lo destruya inmediatamente después de iniciar la conexión.
 */
public class SyncReceiverService extends Service {

    private static final String TAG =
            SyncReceiverService.class.getSimpleName();

    private static final int SYNC_PORT = 8765;

    private static final int NOTIFICATION_ID =
            8765;

    private static final String CHANNEL_ID =
            "ygsync_receiver";

    private SyncWebSocketServer mServer;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(
                TAG,
                "YG Sync: onCreate()"
        );

        showDiagnostic(
                "YG SYNC — INICIANDO SERVICIO"
        );

        /*
         * Primero convertimos el servicio en FOREGROUND.
         *
         * Esto es importante porque START_STICKY por sí solo
         * no garantiza que Android mantenga vivo el servicio.
         */
        try {

            createNotificationChannel();

            Notification notification =
                    createNotification();

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );

            Log.d(
                    TAG,
                    "YG Sync: servicio FOREGROUND activo"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR iniciando foreground",
                    e
            );

            showDiagnostic(
                    "YG SYNC — ERROR FOREGROUND: "
                            + safeMessage(e)
            );

            /*
             * No continuamos iniciando el servidor si no podemos
             * establecer correctamente el servicio foreground.
             */
            return;
        }

        /*
         * Crear el bridge que conecta YG Sync con SmartTube.
         */
        try {

            SyncPlayerBridge bridge =
                    new SyncPlaybackBridge(
                            getApplicationContext()
                    );

            /*
             * Un único servidor WebSocket debe ocupar TCP 8765.
             */
            mServer =
                    new SyncWebSocketServer(
                            SYNC_PORT,
                            bridge,
                            getApplicationContext()
                    );

            Log.d(
                    TAG,
                    "YG Sync: servidor WebSocket creado"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR creando WebSocket",
                    e
            );

            showDiagnostic(
                    "YG SYNC — ERROR CREANDO SERVIDOR: "
                            + safeMessage(e)
            );

            return;
        }

        /*
         * Iniciar WebSocket.
         */
        try {

            mServer.start();

            Log.d(
                    TAG,
                    "YG Sync: WebSocket iniciado en TCP "
                            + SYNC_PORT
            );

            showDiagnostic(
                    "YG SYNC — TCP "
                            + SYNC_PORT
                            + " ACTIVO"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR iniciando WebSocket",
                    e
            );

            showDiagnostic(
                    "YG SYNC — ERROR TCP: "
                            + safeMessage(e)
            );

            /*
             * Si el servidor no pudo arrancar, liberamos
             * la instancia para permitir una futura recreación.
             */
            try {

                if (mServer != null) {
                    mServer.stop();
                }

            } catch (Exception stopError) {

                Log.e(
                        TAG,
                        "YG Sync: error limpiando servidor",
                        stopError
                );
            }

            mServer = null;
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        Log.d(
                TAG,
                "YG Sync: onStartCommand()"
        );

        /*
         * Si Android mata el proceso, solicita que el servicio
         * vuelva a ser creado.
         */
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        Log.d(
                TAG,
                "YG Sync: onDestroy()"
        );

        showDiagnostic(
                "YG SYNC — SERVICIO DETENIDO"
        );

        if (mServer != null) {

            try {

                Log.d(
                        TAG,
                        "YG Sync: deteniendo WebSocket"
                );

                mServer.stop();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG Sync: error al detener WebSocket",
                        e
                );
            }

            mServer = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Crea el canal de notificación requerido por Android 8+.
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "YG Sync",
                        NotificationManager.IMPORTANCE_LOW
                );

        channel.setDescription(
                "Servicio de sincronización de SmartTube"
        );

        channel.setShowBadge(false);

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    /**
     * Notificación permanente del servicio foreground.
     */
    private Notification createNotification() {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "YG Sync Receiver"
                )
                .setContentText(
                        "Esperando conexión del controlador"
                )
                .setSmallIcon(
                        android.R.drawable.ic_media_play
                )
                .setOngoing(true)
                .setCategory(
                        NotificationCompat.CATEGORY_SERVICE
                )
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .build();
    }

    private void showDiagnostic(String message) {

        try {

            Toast.makeText(
                    getApplicationContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync diagnostic display error: "
                            + e.getMessage()
            );
        }
    }

    private String safeMessage(Exception e) {

        if (e == null) {
            return "desconocido";
        }

        String message =
                e.getMessage();

        if (message == null ||
                message.trim().isEmpty()) {

            return e.getClass()
                    .getSimpleName();
        }

        return message;
    }
            }
