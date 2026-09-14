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
 * Arquitectura:
 *
 * UDP 8766 = descubrimiento
 * TCP 8765 = WebSocket de control
 */
public class SyncReceiverService extends Service {

    private static final String TAG =
            SyncReceiverService.class.getSimpleName();

    private static final int SYNC_PORT = 8765;
    private static final int DISCOVERY_PORT = 8766;

    private static final int NOTIFICATION_ID = 8765;

    private static final String CHANNEL_ID =
            "ygsync_receiver";

    private static final String CHANNEL_NAME =
            "YG Sync";

    private SyncWebSocketServer mServer;

    private SyncDiscoveryServer mDiscoveryServer;

    private String mPairingCode = "";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "YG Sync: onCreate()");
        showDiagnostic("YG SYNC — INICIANDO SERVICIO");

        String deviceId =
                SyncDeviceIdentity.getDeviceId(
                        getApplicationContext()
                );

        Log.d(
                TAG,
                "YG Sync: deviceId="
                        + deviceId
        );

        showDiagnostic(
                "YG SYNC — ID: "
                        + deviceId
        );

        mPairingCode =
                SyncPairingManager.generateNewCode(
                        getApplicationContext()
                );

        Log.d(
                TAG,
                "YG Sync: código de pairing="
                        + mPairingCode
        );

        showDiagnostic(
                "YG SYNC — CÓDIGO: "
                        + mPairingCode
        );

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

        startDiscoveryServer();

        startWebSocketServer();
    }

    private void startDiscoveryServer() {

        try {

            mDiscoveryServer =
                    new SyncDiscoveryServer(
                            getApplicationContext()
                    );

            mDiscoveryServer.start();

            Log.d(
                    TAG,
                    "YG Sync: descubrimiento UDP "
                            + DISCOVERY_PORT
                            + " iniciado"
            );

            showDiagnostic(
                    "YG SYNC — UDP "
                            + DISCOVERY_PORT
                            + " ACTIVO"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR iniciando discovery",
                    e
            );

            showDiagnostic(
                    "YG SYNC — ERROR UDP: "
                            + safeMessage(e)
            );

            mDiscoveryServer = null;
        }
    }

    private void startWebSocketServer() {

        try {

            SyncPlayerBridge bridge =
                    new SyncPlaybackBridge(
                            getApplicationContext()
                    );

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

    private boolean startForegroundService() {

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
                        "YG Sync: error deteniendo WebSocket",
                        e
                );
            }

            mServer = null;
        }

        if (mDiscoveryServer != null) {

            try {

                Log.d(
                        TAG,
                        "YG Sync: deteniendo discovery UDP "
                                + DISCOVERY_PORT
                );

                mDiscoveryServer.stop();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG Sync: error deteniendo discovery",
                        e
                );
            }

            mDiscoveryServer = null;
        }

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

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O) {

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
     * Texto de la notificación permanente. Mientras el receptor no
     * tenga ningún controlador emparejado, muestra el código de 6
     * dígitos que hay que escribir en "YG Sync Control" para
     * vincularlo. Una vez emparejado, este texto se puede simplificar
     * más adelante (paso 2.3) para mostrar en su lugar el estado de
     * reproducción.
     */
    private String getNotificationText() {

        if (
                mPairingCode == null ||
                mPairingCode.trim().isEmpty()
        ) {
            return "Esperando conexión del controlador";
        }

        return "Código de emparejamiento: "
                + mPairingCode;
    }

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            return new Notification.Builder(
                    this,
                    CHANNEL_ID
            )
                    .setContentTitle(
                            "YG Sync Receiver"
                    )
                    .setContentText(
                            getNotificationText()
                    )
                    .setSmallIcon(
                            android.R.drawable.ic_media_play
                    )
                    .setOngoing(true)
                    .setVisibility(
                            Notification.VISIBILITY_PUBLIC
                    )
                    .build();

        } else if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.LOLLIPOP
        ) {

            return new Notification.Builder(
                    this
            )
                    .setContentTitle(
                            "YG Sync Receiver"
                    )
                    .setContentText(
                            getNotificationText()
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

            return new Notification.Builder(
                    this
            )
                    .setContentTitle(
                            "YG Sync Receiver"
                    )
                    .setContentText(
                            getNotificationText()
                    )
                    .setSmallIcon(
                            android.R.drawable.ic_media_play
                    )
                    .setOngoing(true)
                    .build();
        }
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
                    "YG Sync: error mostrando diagnóstico: "
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
