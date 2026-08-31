package com.liskovsoft.smartyoutubetv2.tv.sync;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * Servidor WebSocket que corre dentro de cada pantalla (TV).
 * Recibe comandos JSON del teléfono controlador y los ejecuta sobre el reproductor.
 */
public class SyncWebSocketServer extends WebSocketServer {
    private final SyncPlayerBridge mPlayerBridge;

    public SyncWebSocketServer(int port, SyncPlayerBridge playerBridge) {
        super(new InetSocketAddress(port));
        mPlayerBridge = playerBridge;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Un controlador se conectó. Se podría enviar un "hello" de confirmación aquí.
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Conexión cerrada. No se requiere acción para el MVP.
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
        // Nunca dejar que un error de socket tumbe el proceso.
    }

    @Override
    public void onStart() {
        // Servidor arrancado correctamente y escuchando en el puerto configurado.
    }
}
