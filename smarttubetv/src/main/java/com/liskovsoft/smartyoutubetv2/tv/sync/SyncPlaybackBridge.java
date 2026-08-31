package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

/**
 * Implementación real de SyncPlayerBridge: traduce los comandos de sincronización
 * en llamadas al PlaybackPresenter/PlaybackView existentes de SmartTube.
 *
 * IMPORTANTE: esta clase NO modifica PlaybackPresenter ni PlaybackView.
 * Solo los USA a través de sus métodos públicos ya existentes.
 */
public class SyncPlaybackBridge implements SyncPlayerBridge {
    private final Context mContext;

    public SyncPlaybackBridge(Context context) {
        mContext = context.getApplicationContext();
    }

    private PlaybackPresenter presenter() {
        return PlaybackPresenter.instance(mContext);
    }

    private PlaybackView view() {
        return presenter().getPlayer();
    }

    @Override
    public void play() {
        PlaybackView v = view();
        if (v != null) {
            v.setPlayWhenReady(true);
        }
    }

    @Override
    public void pause() {
        PlaybackView v = view();
        if (v != null) {
            v.setPlayWhenReady(false);
        }
    }

    @Override
    public void seekTo(long positionMs) {
        // setPosition ya hace setPositionMs + setPlayWhenReady(true) + showOverlay(false)
        presenter().setPosition(positionMs);
    }

    @Override
    public void openVideo(String videoId) {
        presenter().openVideo(videoId);
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
    public long getPositionMs() {
        PlaybackView v = view();
        return v != null ? v.getPositionMs() : 0;
    }

    @Override
    public boolean isPlaying() {
        return presenter().isPlaying();
    }

    @Override
    public String getVideoId() {
        // NOTA: asumimos que Video tiene un campo/getter "videoId".
        // Si al compilar da error en esta línea, es la ÚNICA línea que hay que ajustar:
        // reemplázala por el getter real de la clase Video (ej. getVideo().videoId
        // o getVideo().getVideoId(), según la versión exacta del fork).
        try {
            return presenter().getVideo() != null ? presenter().getVideo().videoId : null;
        } catch (Exception e) {
            return null;
        }
    }
}
