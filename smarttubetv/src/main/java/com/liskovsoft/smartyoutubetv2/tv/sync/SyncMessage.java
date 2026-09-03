package com.liskovsoft.smartyoutubetv2.tv.sync;

import org.json.JSONObject;

/**

* Representa un mensaje del protocolo YG Sync.

* 

* Formato:

* 

* {

* "type": "play",

* "commandId": "uuid",

* "senderId": "ygsync-controller",

* "timestamp": 123456789,

* "payload": {}

* }
  */
  public final class SyncMessage {
  
  public final String type;
  public final String commandId;
  public final String senderId;
  public final long timestamp;
  public final JSONObject payload;
  
  public SyncMessage(
  String type,
  String commandId,
  String senderId,
  long timestamp,
  JSONObject payload
  ) {
  
   this.type =
         type == null
                 ? ""
                 : type.trim();

 this.commandId =
         commandId == null
                 ? ""
                 : commandId.trim();

 this.senderId =
         senderId == null
                 ? ""
                 : senderId.trim();

 this.timestamp =
         timestamp > 0
                 ? timestamp
                 : System.currentTimeMillis();

 this.payload =
         payload == null
                 ? new JSONObject()
                 : payload;
  
  }
  
  /**
  
  * Crea un SyncMessage a partir de JSON recibido
  
  * por WebSocket.
  
  * 
  
  * Devuelve null si el mensaje no es válido.
    */
    public static SyncMessage fromJson(
    String rawJson
    ) {
    
    if (
    rawJson == null ||
    rawJson.trim().isEmpty()
    ) {
    return null;
    }
    
    try {
    
     JSONObject object =
         new JSONObject(
                 rawJson.trim()
         );

 String type =
         object.optString(
                 "type",
                 ""
         ).trim();

 if (type.isEmpty()) {
     return null;
 }

 String commandId =
         object.optString(
                 "commandId",
                 ""
         ).trim();

 String senderId =
         object.optString(
                 "senderId",
                 ""
         ).trim();

 long timestamp =
         object.optLong(
                 "timestamp",
                 System.currentTimeMillis()
         );

 JSONObject payload =
         object.optJSONObject(
                 "payload"
         );

 if (payload == null) {
     payload =
             new JSONObject();
 }

 return new SyncMessage(
         type,
         commandId,
         senderId,
         timestamp,
         payload
 );
    
    } catch (Exception ignored) {
    
     return null;
    
    }
    }
  
  /**
  
  * Convierte este mensaje nuevamente a JSON.
    */
    public JSONObject toJson() {
    
    JSONObject object =
    new JSONObject();
    
    try {
    
     object.put(
         "type",
         type
 );

 object.put(
         "commandId",
         commandId
 );

 object.put(
         "senderId",
         senderId
 );

 object.put(
         "timestamp",
         timestamp
 );

 object.put(
         "payload",
         payload
 );
    
    } catch (Exception ignored) {
    }
    
    return object;
    }
  
  /**
  
  * Convierte este mensaje a String JSON.
    */
    @Override
    public String toString() {
    
    return toJson().toString();
    }
  }
