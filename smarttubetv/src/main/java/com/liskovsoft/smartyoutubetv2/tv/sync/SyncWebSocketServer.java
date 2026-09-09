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

    private static final String SENDER_ID =
            "ygsync-receiver";

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

        mPlayerBridge =
                playerBridge;

        mContext =
                context.getApplicationContext();

        setReuseAddr(true);

        Log.d(
                TAG,
                "YG Sync: constructor"
                        + " port="
                        + port
        );
    }

    @Override
    public void onOpen(
            WebSocket conn,
            ClientHandshake handshake
    ) {

        String remoteAddress =
                getRemoteAddress(conn);

        Log.d(
                TAG,
                "YG Sync: CLIENTE CONECTADO"
        );

        Log.d(
                TAG,
                "YG Sync: remote="
                        + remoteAddress
        );

        if (handshake != null) {

            Log.d(
                    TAG,
                    "YG Sync: handshake recibido"
            );

            try {

                Log.d(
                        TAG,
                        "YG Sync: handshake resource="
                                + handshake.getResourceDescriptor()
                );

            } catch (Exception e) {

                Log.d(
                        TAG,
                        "YG Sync: no se pudo obtener resource del handshake"
                );
            }
        } else {

            Log.d(
                    TAG,
                    "YG Sync: handshake=null"
            );
        }

        showDiagnostic(
                "YG SYNC — CLIENTE CONECTADO"
        );

        sendHello(conn);
    }

    @Override
    public void onClose(
            WebSocket conn,
            int code,
            String reason,
            boolean remote
    ) {

        String remoteAddress =
                getRemoteAddress(conn);

        String safeReason =
                reason == null
                        ? ""
                        : reason;

        Log.d(
                TAG,
                "YG Sync: CLIENTE DESCONECTADO"
        );

        Log.d(
                TAG,
                "YG Sync: closeCode="
                        + code
                        + " reason="
                        + safeReason
                        + " remote="
                        + remote
        );

        Log.d(
                TAG,
                "YG Sync: remoteAddress="
                        + remoteAddress
        );

        showDiagnostic(
                "YG SYNC — DESCONECTADO "
                        + code
                        + " / "
                        + safeReason
        );
    }

    @Override
    public void onMessage(
            WebSocket conn,
            String message
    ) {

        String remoteAddress =
                getRemoteAddress(conn);

        Log.d(
                TAG,
                "YG Sync: MENSAJE RECIBIDO"
        );

        Log.d(
                TAG,
                "YG Sync: remote="
                        + remoteAddress
        );

        Log.d(
                TAG,
                "YG Sync: message="
                        + message
        );

        SyncMessage parsed =
                SyncMessage.fromJson(message);

        if (parsed == null) {

            Log.e(
                    TAG,
                    "YG Sync: mensaje JSON invalido"
            );

            sendError(
                    conn,
                    "",
                    "INVALID_MESSAGE",
                    "Mensaje JSON invalido"
            );

            return;
        }

        Log.d(
                TAG,
                "YG Sync: comando recibido="
                        + parsed.type
        );

        Log.d(
                TAG,
                "YG Sync: commandId="
                        + parsed.commandId
        );

        if ("ping".equals(parsed.type)) {

            Log.d(
                    TAG,
                    "YG Sync: respondiendo PING"
            );

            sendPong(
                    conn,
                    parsed
            );

            return;
        }

        if (
                "getStatus".equals(parsed.type)
                        ||
                "status".equals(parsed.type)
        ) {

            Log.d(
                    TAG,
                    "YG Sync: enviando STATUS"
            );

            sendStatus(
                    conn,
                    parsed
            );

            return;
        }

        try {

            Log.d(
                    TAG,
                    "YG Sync: ejecutando comando="
                            + parsed.type
            );

            SyncCommand.execute(
                    parsed,
                    mPlayerBridge
            );

            Log.d(
                    TAG,
                    "YG Sync: comando ejecutado="
                            + parsed.type
            );

            sendAck(
                    conn,
                    parsed
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR ejecutando comando "
                            + parsed.type,
                    e
            );

            sendError(
                    conn,
                    parsed.commandId,
                    "COMMAND_FAILED",
                    e.getMessage()
            );
        }
    }

    @Override
    public void onError(
            WebSocket conn,
            Exception ex
    ) {

        String message =
                ex != null
                        ? ex.getMessage()
                        : "desconocido";

        String remoteAddress =
                getRemoteAddress(conn);

        Log.e(
                TAG,
                "YG Sync: ERROR WEBSOCKET"
        );

        Log.e(
                TAG,
                "YG Sync: remote="
                        + remoteAddress
        );

        Log.e(
                TAG,
                "YG Sync: exception="
                        + message,
                ex
        );

        showDiagnostic(
                "YG SYNC — ERROR: "
                        + message
        );
    }

    @Override
    public void onStart() {

        Log.d(
                TAG,
                "YG Sync WebSocket SERVER STARTED"
        );

        Log.d(
                TAG,
                "YG Sync: TCP port="
                        + getPort()
        );

        Log.d(
                TAG,
                "YG Sync: serverAddress="
                        + getAddress()
        );

        showDiagnostic(
                "YG SYNC — TCP OK (puerto "
                        + getPort()
                        + ")"
        );
    }

    private void sendHello(
            WebSocket conn
    ) {

        try {

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "name",
                    "YG Sync SmartTube"
            );

            payload.put(
                    "port",
                    getPort()
            );

            payload.put(
                    "protocol",
                    "ygsync-v1"
            );

            payload.put(
                    "success",
                    true
            );

            JSONObject hello =
                    new JSONObject();

            hello.put(
                    "type",
                    "hello"
            );

            hello.put(
                    "senderId",
                    SENDER_ID
            );

            hello.put(
                    "payload",
                    payload
            );

            String json =
                    hello.toString();

            Log.d(
                    TAG,
                    "YG Sync: enviando HELLO="
                            + json
            );

            conn.send(
                    json
            );

            Log.d(
                    TAG,
                    "YG Sync: HELLO enviado"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR enviando HELLO",
                    e
            );
        }
    }

    private void sendPong(
            WebSocket conn,
            SyncMessage request
    ) {

        try {

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "success",
                    true
            );

            payload.put(
                    "serverTimestampMs",
                    System.currentTimeMillis()
            );

            JSONObject pong =
                    new JSONObject();

            pong.put(
                    "type",
                    "pong"
            );

            pong.put(
                    "commandId",
                    request.commandId
            );

            pong.put(
                    "senderId",
                    SENDER_ID
            );

            pong.put(
                    "payload",
                    payload
            );

            String json =
                    pong.toString();

            Log.d(
                    TAG,
                    "YG Sync: enviando PONG="
                            + json
            );

            conn.send(
                    json
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR enviando PONG",
                    e
            );
        }
    }

    private void sendAck(
            WebSocket conn,
            SyncMessage request
    ) {

        try {

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "success",
                    true
            );

            payload.put(
                    "command",
                    request.type
            );

            JSONObject ack =
                    new JSONObject();

            ack.put(
                    "type",
                    "ack"
            );

            ack.put(
                    "commandId",
                    request.commandId
            );

            ack.put(
                    "senderId",
                    SENDER_ID
            );

            ack.put(
                    "payload",
                    payload
            );

            String json =
                    ack.toString();

            Log.d(
                    TAG,
                    "YG Sync: enviando ACK="
                            + json
            );

            conn.send(
                    json
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR enviando ACK",
                    e
            );
        }
    }

    private void sendStatus(
            WebSocket conn,
            SyncMessage request
    ) {

        try {

            String videoId =
                    null;

            long positionMs =
                    0;

            boolean isPlaying =
                    false;

            try {

                videoId =
                        mPlayerBridge.getVideoId();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG Sync: error obteniendo videoId",
                        e
                );
            }

            try {

                positionMs =
                        mPlayerBridge.getPositionMs();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG Sync: error obteniendo position",
                        e
                );
            }

            try {

                isPlaying =
                        mPlayerBridge.isPlaying();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG Sync: error obteniendo playback",
                        e
                );
            }

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "videoId",
                    videoId == null
                            ? ""
                            : videoId
            );

            payload.put(
                    "positionMs",
                    positionMs
            );

            payload.put(
                    "isPlaying",
                    isPlaying
            );

            payload.put(
                    "success",
                    true
            );

            JSONObject status =
                    new JSONObject();

            status.put(
                    "type",
                    "status"
            );

            status.put(
                    "commandId",
                    request.commandId
            );

            status.put(
                    "senderId",
                    SENDER_ID
            );

            status.put(
                    "payload",
                    payload
            );

            String json =
                    status.toString();

            Log.d(
                    TAG,
                    "YG Sync: enviando STATUS="
                            + json
            );

            conn.send(
                    json
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR creando STATUS",
                    e
            );

            sendError(
                    conn,
                    request.commandId,
                    "STATUS_FAILED",
                    e.getMessage()
            );
        }
    }

    private void sendError(
            WebSocket conn,
            String commandId,
            String errorCode,
            String message
    ) {

        try {

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "success",
                    false
            );

            payload.put(
                    "errorCode",
                    errorCode
            );

            payload.put(
                    "message",
                    message == null
                            ? ""
                            : message
            );

            JSONObject error =
                    new JSONObject();

            error.put(
                    "type",
                    "error"
            );

            error.put(
                    "commandId",
                    commandId == null
                            ? ""
                            : commandId
            );

            error.put(
                    "senderId",
                    SENDER_ID
            );

            error.put(
                    "payload",
                    payload
            );

            String json =
                    error.toString();

            Log.e(
                    TAG,
                    "YG Sync: enviando ERROR="
                            + json
            );

            conn.send(
                    json
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "YG Sync: ERROR enviando mensaje de error",
                    e
            );
        }
    }

    private String getRemoteAddress(
            WebSocket conn
    ) {

        if (conn == null) {
            return "null";
        }

        try {

            InetSocketAddress address =
                    conn.getRemoteSocketAddress();

            if (address == null) {
                return "null";
            }

            return address.toString();

        } catch (Exception e) {

            return "desconocido";
        }
    }

    private void showDiagnostic(
            String message
    ) {

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

                Log.e(
                        TAG,
                        "YG Sync diagnostic display error: "
                                + e.getMessage()
                );
            }
        });
    }
}
