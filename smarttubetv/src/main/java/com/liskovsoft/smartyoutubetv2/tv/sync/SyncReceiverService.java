package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**

* Servicio Android que mantiene activo el servidor WebSocket

* de YG Sync desde el arranque de SmartTube.

* 

* Transporte:

* 

* TCP 8765 = WebSocket

* 

* UDP 8766 = descubrimiento de dispositivos
  */
  public class SyncReceiverService extends Service {
  
  private static final String TAG =
  SyncReceiverService.class.getSimpleName();
  
  private static final int SYNC_PORT =
  8765;
  
  private SyncWebSocketServer mServer;
  
  @Override
  public void onCreate() {
  
   super.onCreate();

 Log.i(
         TAG,
         "YG Sync: iniciando servicio"
 );

 try {

     SyncPlayerBridge bridge =
             new SyncPlaybackBridge(
                     getApplicationContext()
             );

     mServer =
             new SyncWebSocketServer(
                     SYNC_PORT,
                     bridge
             );

     mServer.start();

     Log.i(
             TAG,
             "YG Sync: WebSocket iniciado en TCP "
                     + SYNC_PORT
     );

 } catch (Exception e) {

     Log.e(
             TAG,
             "YG Sync: ERROR iniciando WebSocket",
             e
     );

     mServer =
             null;
 }
  
  }
  
  @Override
  public int onStartCommand(
  Intent intent,
  int flags,
  int startId
  ) {
  
   Log.i(
         TAG,
         "YG Sync: servicio activo"
 );

 return START_STICKY;
  
  }
  
  @Override
  public void onDestroy() {
  
   Log.i(
         TAG,
         "YG Sync: deteniendo servicio"
 );

 if (mServer != null) {

     try {

         mServer.stop();

     } catch (Exception e) {

         Log.e(
                 TAG,
                 "YG Sync: error deteniendo WebSocket",
                 e
         );
     }

     mServer =
             null;
 }

 super.onDestroy();
  
  }
  
  @Override
  public IBinder onBind(
  Intent intent
  ) {
  
   return null;
  
  }
  }
