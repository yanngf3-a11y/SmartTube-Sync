package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

/**

* Implementación real de SyncPlayerBridge.

* 

* Traduce los comandos de sincronización en llamadas

* al PlaybackPresenter / PlaybackView de SmartTube.

* 

* Ninguna clase de red accede directamente al reproductor.

* Todas las operaciones pasan por esta clase.
  */
  public class SyncPlaybackBridge implements SyncPlayerBridge {
  
  private final Context mContext;
  
  public SyncPlaybackBridge(Context context) {
  mContext =
  context.getApplicationContext();
  }
  
  private PlaybackPresenter presenter() {
  return PlaybackPresenter.instance(
  mContext
  );
  }
  
  private PlaybackView view() {
  return presenter().getPlayer();
  }
  
  @Override
  public void play() {
  
   PlaybackView v =
         view();

 if (v != null) {

     v.setPlayWhenReady(
             true
     );
 }
  
  }
  
  @Override
  public void pause() {
  
   PlaybackView v =
         view();

 if (v != null) {

     v.setPlayWhenReady(
             false
     );
 }
  
  }
  
  @Override
  public void seekTo(
  long positionMs
  ) {
  
   PlaybackView v =
         view();

 if (v != null) {

     v.setPositionMs(
             Math.max(
                     0,
                     positionMs
             )
     );
 }
  
  }
  
  @Override
  public void openVideo(
  String videoId
  ) {
  
   if (
         videoId == null ||
         videoId.trim().isEmpty()
 ) {
     return;
 }

 presenter().openVideo(
         videoId.trim()
 );
  
  }
  
  @Override
  public void next() {
  
   presenter().onNextClicked();
  
  }
  
  @Override
  public void previous() {
  
   presenter().onPreviousClicked();
  
  }
  
  @Override
  public void setVolume(
  float volume
  ) {
  
   PlaybackView v =
         view();

 if (v == null) {
     return;
 }

 float safeVolume =
         Math.max(
                 0.0f,
                 Math.min(
                         1.0f,
                         volume
                 )
         );

 v.setVolume(
         safeVolume
 );
  
  }
  
  @Override
  public long getPositionMs() {
  
   PlaybackView v =
         view();

 return v != null
         ? Math.max(
                 0,
                 v.getPositionMs()
         )
         : 0;
  
  }
  
  @Override
  public boolean isPlaying() {
  
   try {

     return presenter()
             .isPlaying();

 } catch (Exception ignored) {

     return false;
 }
  
  }
  
  @Override
  public String getVideoId() {
  
   try {

     return presenter().getVideo() != null
             ? presenter().getVideo().videoId
             : null;

 } catch (Exception ignored) {

     return null;
 }
  
  }
  
  /**
  
  * Obtiene el volumen actual del reproductor.
    */
    public float getVolume() {
    
    try {
    
     PlaybackView v =
         view();

 if (v == null) {
     return 0.0f;
 }

 return Math.max(
         0.0f,
         Math.min(
                 1.0f,
                 v.getVolume()
         )
 );
    
    } catch (Exception ignored) {
    
     return 0.0f;
    
    }
    }
  }
