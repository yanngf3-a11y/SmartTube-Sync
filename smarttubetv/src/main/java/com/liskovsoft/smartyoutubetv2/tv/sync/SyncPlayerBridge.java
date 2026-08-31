package com.liskovsoft.smartyoutubetv2.tv.sync;

/**
 * Adaptador entre la capa de red (SyncReceiverService) y el reproductor real de SmartTube.
 * Ninguna clase de red debe llamar a PlaybackPresenter directamente: siempre pasa por aquí.
 */
public interface SyncPlayerBridge {
    void play();
    void pause();
    void seekTo(long positionMs);
    void openVideo(String videoId);
    void next();
    void previous();
    long getPositionMs();
    boolean isPlaying();
    String getVideoId();
}
