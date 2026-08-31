package com.liskovsoft.smarttubesync.controller;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Mantiene una ScreenConnection por cada pantalla configurada y transmite
 * los mismos comandos a todas al mismo tiempo (broadcast simple).
 */
public class ScreenGroup {
    private static final int SYNC_PORT = 8765;

    private final List<ScreenConnection> mConnections = new ArrayList<>();
    private final ScreenConnection.StatusListener mListener;

    public ScreenGroup(ScreenConnection.StatusListener listener) {
        mListener = listener;
    }

    /** Cierra conexiones previas y se conecta de nuevo a la lista de IPs dada. */
    public void connectAll(List<String> screenIps) {
        disconnectAll();

        for (String ip : screenIps) {
            if (ip == null || ip.trim().isEmpty()) {
                continue;
            }
            try {
                ScreenConnection conn = new ScreenConnection(ip.trim(), SYNC_PORT, mListener);
                mConnections.add(conn);
                conn.connect();
            } catch (Exception e) {
                if (mListener != null) {
                    mListener.onStatus(ip, "no se pudo iniciar conexion: " + e.getMessage());
                }
            }
        }
    }

    public void disconnectAll() {
        for (ScreenConnection conn : mConnections) {
            try {
                conn.close();
            } catch (Exception ignored) { }
        }
        mConnections.clear();
    }

    public void play() {
        broadcast("play", null);
    }

    public void pause() {
        broadcast("pause", null);
    }

    public void next() {
        broadcast("next", null);
    }

    public void previous() {
        broadcast("previous", null);
    }

    public void openVideo(String videoId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("videoId", videoId);
        } catch (Exception ignored) { }
        broadcast("open", payload);
    }

    /**
     * Manda un comando "sync" con la posicion actual reportada por la primera
     * pantalla conectada, para realinear al resto. Version simple (MVP):
     * en el futuro se puede pedir posicion real a cada pantalla antes de esto.
     */
    public void forceSync(long referencePositionMs) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("targetPositionMs", referencePositionMs);
            payload.put("serverTimestampMs", System.currentTimeMillis());
        } catch (Exception ignored) { }
        broadcast("sync", payload);
    }

    private void broadcast(String type, JSONObject payload) {
        for (ScreenConnection conn : mConnections) {
            conn.sendCommand(type, payload);
        }
    }
}
