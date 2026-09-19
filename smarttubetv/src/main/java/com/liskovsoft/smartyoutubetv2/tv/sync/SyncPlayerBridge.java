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

  /**
   * Arranca la reproducción en un instante preciso (timestamp en
   * el reloj de ESTE dispositivo, ya traducido por el Controller
   * usando el offset de reloj). Se usa después de prepareVideo():
   * el video ya está cargado y en pausa, y esto dispara el play()
   * real exactamente a esa hora.
   */
  void playAt(long timestampMs);

  /**
   * True solo si el motor de reproducción terminó de cargar
   * "videoId" y ya no está bufereando (no alcanza con que haya
   * aceptado el ID: eso pasa casi al instante, mucho antes de que
   * el video pueda arrancar sin cortes). Se usa para decidir
   * cuándo mandar "ready" al Controller, en vez de mandarlo apenas
   * se acepta el comando.
   */
  boolean isReadyToPlay(String videoId);

  void next();
  
  void previous();
  
  void setVolume(float volume);
  
  long getPositionMs();
  
  boolean isPlaying();
  
  String getVideoId();
  }
