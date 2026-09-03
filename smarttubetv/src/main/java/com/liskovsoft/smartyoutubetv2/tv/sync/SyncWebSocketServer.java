package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * Servidor WebSocket que corre dentro de cada pantalla (TV).
 * Recibe comandos JSON del teléfono controlador y los ejecuta sobre el reproductor.
 *
 * YG Sync diagnostic: los callbacks de WebSocketServer corren en un hilo
 * propio de la librería, no en el hilo principal. bind() ocurre de forma
 * asíncrona dentro de start(), así que un fallo de bind (ej. puerto
 * ocupado) NO lanza una excepción capturable en el try/catch del llamador:
 * se reporta únicamente vía onError(). Antes ese método estaba vacío,
 * por lo que un fallo de arranque era invisible. Ahora se muestra en
 * pantalla con Toast (posteado al hilo principal con Handler) para poder
 * diagnosticar sin Logcat.
 */
public class SyncWebSocketServer extends WebSocketServer {

    private static final String TAG =
            SyncWebSocketServer.class.getSimpleName();

    private final SyncPlayerBridge mPlayerBridge;
    private final Context mContext;
    private final Handler mMainHandler =
            new Handler(Looper.getMainLooper());

    public SyncWebSocketServer(
            int port,
            SyncPlayerBridge playerBridge,
            Context context
    ) {
        super(new InetSocketAddress(port));
        mPlayerBridge = playerBridge;
        mContext = context.getApplicationContext();
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Un controlador se conectó.
        Log.d(TAG, "YG Sync: cliente conectado");
        showDiagnostic("YG SYNC — CLIENTE CONECTADO");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Conexión cerrada.
        Log.d(TAG, "YG Sync: cliente desconectado (" + reason + ")");
        showDiagnostic("YG SYNC — CLIENTE DESCONECTADO");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        SyncMessage parsed = SyncMessage.fromJson(message);
        if (parsed != null) {
            SyncCommand.execute(parsed, mPlayerBridge);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // YG Sync diagnostic: antes este método estaba vacío y tragaba
        // en silencio fallos de bind del puerto. Ahora se reporta.
        String message =
                ex != null
                        ? ex.getMessage()
                        : "desconocido";

        Log.e(TAG, "YG Sync WebSocket error: " + message, ex);

        showDiagnostic("YG SYNC — ERROR: " + message);
    }

    @Override
    public void onStart() {
        // Servidor arrancado correctamente y escuchando en el puerto configurado.
        Log.d(TAG, "YG Sync WebSocket server started on port " + getPort());
        showDiagnostic("YG SYNC — TCP OK (puerto " + getPort() + ")");
    }

    private void showDiagnostic(String message) {

        if (mContext == null) {
            return;
        }

        mMainHandler.post(() -> {
            try {
                Toast.makeText(
                        mContext,
                        message,
                        Toast.LENGTH_SHORT
                ).show();
            } catch (Exception e) {
                Log.e(TAG, "YG Sync diagnostic display error: " + e.getMessage());
            }
        });
    }
}
