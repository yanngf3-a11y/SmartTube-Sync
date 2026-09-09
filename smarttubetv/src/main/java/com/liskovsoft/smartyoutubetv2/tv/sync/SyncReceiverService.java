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

/**
 * Servicio foreground de YG Sync.
 *
 * Mantiene activo el servidor WebSocket que recibe los comandos
 * del YG Sync Controller.
 *
 * Arquitectura:
 *
 * UDP 8766 = descubrimiento
 * TCP 8765 = WebSocket de control
 */
public class SyncReceiverService extends Service {

    private static final String TAG =
            SyncReceiverService.class.getSimpleName();

    private static final int SYNC_PORT = 8765;

    private static final int NOTIFICATION_ID = 8765;

    private static final String CHANNEL_ID =
            "ygsync_receiver";

    private static final String CHANNEL_NAME =
            "YG Sync";

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
         * El servicio debe convertirse en foreground antes
         * de iniciar el servidor WebSocket.
         */
        if (!startForegroundService()) {

            Log.e(
                    TAG,
                    "YG Sync: no se pudo iniciar foreground"
            );

            showDiagnostic(
                    "YG SYNC — ERROR FOREGROUND"
            );

            return;
        }

        /*
         * Crear el bridge entre YG Sync y el reproductor
         * interno de SmartTube.
         */
        try {

            SyncPlayerBridge bridge =
                    new SyncPlaybackBridge(
                            getApplicationContext()
                    );

            /*
             * TCP 8765:
             * servidor WebSocket único.
             */
            mServer =
                    new SyncWebSocketServer(
                            SYNC_PORT,
                            bridge,
                            getApplicationContext()
                    );

            Log.d(
                    TAG,
                    "YG Sync: servidor WebSocket creado en TCP "
                            + SYNC_PORT
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR creando servidor WebSocket",
                    e
            );

            showDiagnostic(
                    "YG SYNC — ERROR CREANDO SERVIDOR: "
                            + safeMessage(e)
            );

            mServer = null;

            return;
        }

        /*
         * Iniciar servidor WebSocket.
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

            try {

                if (mServer != null) {
                    mServer.stop();
                }

            } catch (Exception stopError) {

                Log.e(
                        TAG,
                        "YG Sync: error limpiando WebSocket",
                        stopError
                );
            }

            mServer = null;
        }
    }

    /**
     * Inicia correctamente el servicio como foreground.
     */
    private boolean startForegroundService() {

        try {

            createNotificationChannel();

            Notification notification =
                    createNotification();

            /*
             * startForeground() funciona desde API 21.
             *
             * El tipo mediaPlayback ya está declarado en el
             * AndroidManifest.xml:
             *
             * android:foregroundServiceType="mediaPlayback"
             */
            startForeground(
                    NOTIFICATION_ID,
                    notification
            );

            Log.d(
                    TAG,
                    "YG Sync: servicio foreground activo"
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR en startForeground()",
                    e
            );

            return false;
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
         * Si Android destruye el servicio, solicita que
         * vuelva a crearlo cuando sea posible.
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

        /*
         * Detener WebSocket correctamente.
         */
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
                        "YG Sync: error deteniendo WebSocket",
                        e
                );
            }

            mServer = null;
        }

        /*
         * Quitar el servicio foreground.
         */
        try {

            stopForeground(true);

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: error quitando foreground",
                    e
            );
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Crea el canal de notificación requerido desde Android 8.
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        try {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Servicio receptor de YG Sync"
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

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: error creando canal",
                    e
            );
        }
    }

    /**
     * Crea la notificación permanente del servicio.
     *
     * Se utiliza Notification.Builder nativo para evitar
     * depender de una versión concreta de androidx.core.
     */
    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            return new Notification.Builder(
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
                    .setVisibility(
                            Notification.VISIBILITY_PUBLIC
                    )
                    .build();

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            return new Notification.Builder(
                    this
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
                    .setVisibility(
                            Notification.VISIBILITY_PUBLIC
                    )
                    .build();

        } else {

            /*
             * Android 17-20.
             *
             * No se utilizan métodos introducidos posteriormente.
             */
            return new Notification.Builder(
                    this
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
                    .build();
        }
    }

    /**
     * Muestra información de diagnóstico durante las pruebas.
     */
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
                    "YG Sync: error mostrando diagnóstico: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Obtiene un mensaje seguro de una excepción.
     */
    private String safeMessage(Exception e) {

        if (e == null) {
            return "desconocido";
        }

        String message =
                e.getMessage();

        if (
                message == null ||
                message.trim().isEmpty()
        ) {

            return e.getClass()
                    .getSimpleName();
        }

        return message;
    }
}
