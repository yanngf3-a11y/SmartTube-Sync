package com.liskovsoft.smartyoutubetv2.tv.sync;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;

/**

* Servidor WebSocket de YG Sync.

* 

* Corre dentro de cada pantalla/TV y recibe comandos JSON

* enviados por el controlador Android.

* 

* Puerto utilizado por YG Sync:

* TCP 8765

* 

* El descubrimiento de dispositivos continúa utilizando UDP 8766.
  */
  public class SyncWebSocketServer extends WebSocketServer {
  
  private final SyncPlayerBridge mPlayerBridge;
  
  public SyncWebSocketServer(
  int port,
  SyncPlayerBridge playerBridge
  ) {
  super(new InetSocketAddress(port));
  
   mPlayerBridge = playerBridge;

 setReuseAddr(true);
  
  }
  
  @Override
  public void onOpen(
  WebSocket conn,
  ClientHandshake handshake
  ) {
  
   if (conn == null) {
     return;
 }

 /*
  * Confirmamos inmediatamente la conexión.
  *
  * Esto permite que el controlador sepa que realmente
  * está conectado a un receptor YG Sync.
  */
 try {

     JSONObject response =
             new JSONObject();

     response.put(
             "type",
             "hello"
     );

     response.put(
             "commandId",
             ""
     );

     response.put(
             "senderId",
             "ygsync-receiver"
     );

     response.put(
             "timestamp",
             System.currentTimeMillis()
     );

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

     response.put(
             "payload",
             payload
     );

     conn.send(
             response.toString()
     );

 } catch (Exception ignored) {
     // Nunca dejar que un error de respuesta cierre el servidor.
 }
  
  }
  
  @Override
  public void onClose(
  WebSocket conn,
  int code,
  String reason,
  boolean remote
  ) {
  // La conexión se cerró. No se requiere ninguna acción.
  }
  
  @Override
  public void onMessage(
  WebSocket conn,
  String message
  ) {
  
   if (
         conn == null ||
         message == null ||
         message.trim().isEmpty()
 ) {
     return;
 }

 SyncMessage parsed =
         SyncMessage.fromJson(message);

 if (parsed == null) {

     sendError(
             conn,
             "",
             "INVALID_MESSAGE",
             "Mensaje JSON inválido"
     );

     return;
 }

 /*
  * PING no necesita pasar por el reproductor.
  * Respondemos inmediatamente para medir latencia.
  */
 if ("ping".equalsIgnoreCase(parsed.type)) {

     sendPong(
             conn,
             parsed.commandId
     );

     return;
 }

 /*
  * GET STATUS devuelve el estado actual del reproductor.
  */
 if (
         "getStatus".equalsIgnoreCase(parsed.type) ||
         "status".equalsIgnoreCase(parsed.type)
 ) {

     sendStatus(
             conn,
             parsed.commandId
     );

     return;
 }

 /*
  * Ejecutamos el comando sobre SmartTube.
  */
 try {

     SyncCommand.execute(
             parsed,
             mPlayerBridge
     );

     /*
      * ACK de aplicación.
      *
      * Importante:
      * recibir este ACK significa que el servidor recibió
      * y procesó el comando, no simplemente que el socket
      * pudo escribir los datos.
      */
     sendAck(
             conn,
             parsed.commandId,
             parsed.type
     );

 } catch (Exception error) {

     sendError(
             conn,
             parsed.commandId,
             "COMMAND_ERROR",
             error.getMessage()
     );
 }
  
  }
  
  @Override
  public void onError(
  WebSocket conn,
  Exception ex
  ) {
  
   /*
  * Los errores de una conexión individual no deben
  * tumbar el proceso de SmartTube.
  */
  
  }
  
  @Override
  public void onStart() {
  /*
  * Servidor WebSocket iniciado correctamente.
  */
  }
  
  /**
  
  * Respuesta PONG para comprobar conectividad y latencia.
    */
    private void sendPong(
    WebSocket conn,
    String commandId
    ) {
    
    if (conn == null || !conn.isOpen()) {
    return;
    }
    
    try {
    
     JSONObject response =
         new JSONObject();

 response.put(
         "type",
         "pong"
 );

 response.put(
         "commandId",
         commandId == null
                 ? ""
                 : commandId
 );

 response.put(
         "senderId",
         "ygsync-receiver"
 );

 response.put(
         "timestamp",
         System.currentTimeMillis()
 );

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

 response.put(
         "payload",
         payload
 );

 conn.send(
         response.toString()
 );
    
    } catch (Exception ignored) {
    }
    }
  
  /**
  
  * ACK de comando procesado.
    */
    private void sendAck(
    WebSocket conn,
    String commandId,
    String commandType
    ) {
    
    if (conn == null || !conn.isOpen()) {
    return;
    }
    
    try {
    
     JSONObject response =
         new JSONObject();

 response.put(
         "type",
         "ack"
 );

 response.put(
         "commandId",
         commandId == null
                 ? ""
                 : commandId
 );

 response.put(
         "senderId",
         "ygsync-receiver"
 );

 response.put(
         "timestamp",
         System.currentTimeMillis()
 );

 JSONObject payload =
         new JSONObject();

 payload.put(
         "success",
         true
 );

 payload.put(
         "command",
         commandType == null
                 ? ""
                 : commandType
 );

 response.put(
         "payload",
         payload
 );

 conn.send(
         response.toString()
 );
    
    } catch (Exception ignored) {
    }
    }
  
  /**
  
  * Estado actual del reproductor.
    */
    private void sendStatus(
    WebSocket conn,
    String commandId
    ) {
    
    if (
    conn == null ||
    !conn.isOpen() ||
    mPlayerBridge == null
    ) {
    return;
    }
    
    try {
    
     JSONObject response =
         new JSONObject();

 response.put(
         "type",
         "status"
 );

 response.put(
         "commandId",
         commandId == null
                 ? ""
                 : commandId
 );

 response.put(
         "senderId",
         "ygsync-receiver"
 );

 response.put(
         "timestamp",
         System.currentTimeMillis()
 );

 JSONObject payload =
         new JSONObject();

 payload.put(
         "success",
         true
 );

 payload.put(
         "videoId",
         safeString(
                 mPlayerBridge.getVideoId()
         )
 );

 payload.put(
         "positionMs",
         Math.max(
                 0,
                 mPlayerBridge.getPositionMs()
         )
 );

 payload.put(
         "isPlaying",
         mPlayerBridge.isPlaying()
 );

 response.put(
         "payload",
         payload
 );

 conn.send(
         response.toString()
 );
    
    } catch (Exception ignored) {
    }
    }
  
  /**
  
  * Envía un error estructurado al controlador.
    */
    private void sendError(
    WebSocket conn,
    String commandId,
    String errorCode,
    String errorMessage
    ) {
    
    if (conn == null || !conn.isOpen()) {
    return;
    }
    
    try {
    
     JSONObject response =
         new JSONObject();

 response.put(
         "type",
         "error"
 );

 response.put(
         "commandId",
         commandId == null
                 ? ""
                 : commandId
 );

 response.put(
         "senderId",
         "ygsync-receiver"
 );

 response.put(
         "timestamp",
         System.currentTimeMillis()
 );

 JSONObject payload =
         new JSONObject();

 payload.put(
         "success",
         false
 );

 payload.put(
         "errorCode",
         errorCode == null
                 ? "UNKNOWN_ERROR"
                 : errorCode
 );

 payload.put(
         "message",
         errorMessage == null
                 ? ""
                 : errorMessage
 );

 response.put(
         "payload",
         payload
 );

 conn.send(
         response.toString()
 );
    
    } catch (Exception ignored) {
    }
    }
  
  private String safeString(
  String value
  ) {
  
   return value == null
         ? ""
         : value;
  
  }
     }
