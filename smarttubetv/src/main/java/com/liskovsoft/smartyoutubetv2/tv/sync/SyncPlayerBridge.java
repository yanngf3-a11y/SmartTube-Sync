package com.liskovsoft.smartyoutubetv2.tv.sync;

/**

* Adaptador entre la capa de red (SyncReceiverService)

* y el reproductor real de SmartTube.

* 

* Ninguna clase de red debe llamar directamente a

* PlaybackPresenter. Todas las operaciones pasan por

* esta interfaz.
  */
  public interface SyncPlayerBridge {
  
  void play();
  
  void pause();
  
  void seekTo(long positionMs);
  
  void openVideo(String videoId);

  /**
   * Igual que openVideo, pero deja el video en pausa apenas carga
   * (no arranca solo). Se usa junto con "playAt" (Fase 3.3): todas
   * las TVs preparan el mismo video de antemano, y recién arrancan
   * todas juntas cuando llega la orden de reproducir.
   */
  void prepareVideo(String videoId);
  
  void next();
  
  void previous();
  
  void setVolume(float volume);
  
  long getPositionMs();
  
  boolean isPlaying();
  
  String getVideoId();
  }
