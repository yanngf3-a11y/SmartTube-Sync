package com.liskovsoft.smartyoutubetv2.tv.sync;

import org.json.JSONObject;

/**
 * Representa un mensaje del protocolo de sincronización.
 * Formato: { type, commandId, senderId, timestamp, payload }
 */
public final class SyncMessage {
    public final String type;
    public final String commandId;
    public final String senderId;
    public final long timestamp;
    public final JSONObject payload;

    public SyncMessage(String type, String commandId, String senderId, long timestamp, JSONObject payload) {
        this.type = type;
        this.commandId = commandId;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.payload = payload == null ? new JSONObject() : payload;
    }

    /**
     * Crea un SyncMessage a partir de un string JSON recibido por el WebSocket.
     * Devuelve null si el mensaje no se pudo interpretar (evita crashear el servicio).
     */
    public static SyncMessage fromJson(String rawJson) {
        try {
            JSONObject o = new JSONObject(rawJson);
            String type = o.optString("type", "");
            String commandId = o.optString("commandId", "");
            String senderId = o.optString("senderId", "");
            long timestamp = o.optLong("timestamp", System.currentTimeMillis());
            JSONObject payload = o.optJSONObject("payload");
            if (type.isEmpty()) {
                return null;
            }
            return new SyncMessage(type, commandId, senderId, timestamp, payload);
        } catch (Exception e) {
            return null;
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            o.put("commandId", commandId);
            o.put("senderId", senderId);
            o.put("timestamp", timestamp);
            o.put("payload", payload);
        } catch (Exception ignored) { }
        return o;
    }
}
