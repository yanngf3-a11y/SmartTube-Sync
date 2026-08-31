package com.liskovsoft.smarttubesync.controller;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

/**
 * Conexión WebSocket hacia una sola pantalla receptora (SyncReceiverService).
 * El controlador crea una instancia de esta clase por cada pantalla configurada.
 */
public class ScreenConnection extends WebSocketClient {
    public interface StatusListener {
        void onStatus(String screenIp, String status);
    }

    private final String mScreenIp;
    private final StatusListener mListener;

    public ScreenConnection(String screenIp, int port, StatusListener listener) throws URISyntaxException {
        super(new URI("ws://" + screenIp + ":" + port));
        mScreenIp = screenIp;
        mListener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        notifyStatus("conectado");
    }

    @Override
    public void onMessage(String message) {
        // El receptor no suele responder nada por ahora; reservado para futuro (estado, ack).
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        notifyStatus("desconectado");
    }

    @Override
    public void onError(Exception ex) {
        notifyStatus("error: " + (ex != null ? ex.getMessage() : "desconocido"));
    }

    private void notifyStatus(String status) {
        if (mListener != null) {
            mListener.onStatus(mScreenIp, status);
        }
    }

    /** Envía un comando con el mismo formato JSON que espera SyncReceiverService. */
    public void sendCommand(String type, JSONObject payload) {
        if (!isOpen()) {
            notifyStatus("no conectado, comando descartado");
            return;
        }

        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            msg.put("commandId", UUID.randomUUID().toString());
            msg.put("senderId", "phone-controller");
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("payload", payload == null ? new JSONObject() : payload);
            send(msg.toString());
        } catch (Exception e) {
            notifyStatus("error al enviar: " + e.getMessage());
        }
    }
}
