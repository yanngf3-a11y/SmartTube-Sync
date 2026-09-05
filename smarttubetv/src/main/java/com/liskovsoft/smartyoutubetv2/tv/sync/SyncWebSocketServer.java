package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;

public class SyncWebSocketServer extends WebSocketServer {

    private static final String TAG =
            SyncWebSocketServer.class.getSimpleName();

    private static final String SENDER_ID = "ygsync-receiver";

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
        Log.d(TAG, "YG Sync: cliente conectado");
        showDiagnostic("YG SYNC — CLIENTE CONECTADO");
        sendHello(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "YG Sync: cliente desconectado (" + reason + ")");
        showDiagnostic("YG SYNC — CLIENTE DESCONECTADO");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        SyncMessage parsed = SyncMessage.fromJson(message);

        if (parsed == null) {
            Log.e(TAG, "YG Sync: mensaje invalido: " + message);
            sendError(conn, "", "INVALID_MESSAGE", "Mensaje JSON invalido");
            return;
        }

        Log.d(TAG, "YG Sync: comando recibido: " + parsed.type);

        if ("ping".equals(parsed.type)) {
            sendPong(conn, parsed);
            return;
        }

        if ("getStatus".equals(parsed.type) || "status".equals(parsed.type)) {
            sendStatus(conn, parsed);
            return;
        }

        try {
            SyncCommand.execute(parsed, mPlayerBridge);
            sendAck(conn, parsed);
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: error ejecutando comando " + parsed.type + ": " + e.getMessage());
            sendError(conn, parsed.commandId, "COMMAND_FAILED", e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String message = ex != null ? ex.getMessage() : "desconocido";
        Log.e(TAG, "YG Sync WebSocket error: " + message, ex);
        showDiagnostic("YG SYNC — ERROR: " + message);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "YG Sync WebSocket server started on port " + getPort());
        showDiagnostic("YG SYNC — TCP OK (puerto " + getPort() + ")");
    }

    private void sendHello(WebSocket conn) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("name", "YG Sync SmartTube");
            payload.put("port", getPort());
            payload.put("protocol", "ygsync-v1");
            payload.put("success", true);

            JSONObject hello = new JSONObject();
            hello.put("type", "hello");
            hello.put("senderId", SENDER_ID);
            hello.put("payload", payload);

            conn.send(hello.toString());
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: error enviando hello: " + e.getMessage());
        }
    }

    private void sendPong(WebSocket conn, SyncMessage request) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("success", true);
            payload.put("serverTimestampMs", System.currentTimeMillis());

            JSONObject pong = new JSONObject();
            pong.put("type", "pong");
            pong.put("commandId", request.commandId);
            pong.put("senderId", SENDER_ID);
            pong.put("payload", payload);

            conn.send(pong.toString());
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: error enviando pong: " + e.getMessage());
        }
    }

    private void sendAck(WebSocket conn, SyncMessage request) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("success", true);
            payload.put("command", request.type);

            JSONObject ack = new JSONObject();
            ack.put("type", "ack");
            ack.put("commandId", request.commandId);
            ack.put("senderId", SENDER_ID);
            ack.put("payload", payload);

            conn.send(ack.toString());
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: error enviando ack: " + e.getMessage());
        }
    }

    private void sendStatus(WebSocket conn, SyncMessage request) {
        try {
            String videoId = null;
            long positionMs = 0;
            boolean isPlaying = false;

            try {
                videoId = mPlayerBridge.getVideoId();
            } catch (Exception ignored) {
            }

            try {
                positionMs = mPlayerBridge.getPositionMs();
            } catch (Exception ignored) {
            }

            try {
                isPlaying = mPlayerBridge.isPlaying();
            } catch (Exception ignored) {
            }

            JSONObject payload = new JSONObject();
            payload.put("videoId", videoId == null ? "" : videoId);
            payload.put("positionMs", positionMs);
            payload.put("isPlaying", isPlaying);
            payload.put("success", true);

            JSONObject status = new JSONObject();
            status.put("type", "status");
            status.put("commandId", request.commandId);
            status.put("senderId", SENDER_ID);
            status.put("payload", payload);

            conn.send(status.toString());
        } catch (Exception e) {
            sendError(conn, request.commandId, "STATUS_FAILED", e.getMessage());
        }
    }

    private void sendError(WebSocket conn, String commandId, String errorCode, String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("success", false);
            payload.put("errorCode", errorCode);
            payload.put("message", message == null ? "" : message);

            JSONObject error = new JSONObject();
            error.put("type", "error");
            error.put("commandId", commandId == null ? "" : commandId);
            error.put("senderId", SENDER_ID);
            error.put("payload", payload);

            conn.send(error.toString());
        } catch (Exception e) {
            Log.e(TAG, "YG Sync: error enviando error: " + e.getMessage());
        }
    }

    private void showDiagnostic(String message) {
        if (mContext == null) {
            return;
        }

        mMainHandler.post(() -> {
            try {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "YG Sync diagnostic display error: " + e.getMessage());
            }
        });
    }
                      }
