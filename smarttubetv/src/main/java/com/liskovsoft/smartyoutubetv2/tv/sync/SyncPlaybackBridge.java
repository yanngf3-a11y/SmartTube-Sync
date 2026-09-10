package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

public class SyncPlaybackBridge implements SyncPlayerBridge {

    private final Context mContext;

    private final Handler mMainHandler =
            new Handler(Looper.getMainLooper());

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

        mMainHandler.post(() -> {

            try {

                PlaybackView v =
                        view();

                if (v != null) {

                    v.setPlayWhenReady(
                            true
                    );
                }

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void pause() {

        mMainHandler.post(() -> {

            try {

                PlaybackView v =
                        view();

                if (v != null) {

                    v.setPlayWhenReady(
                            false
                    );
                }

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void seekTo(
            long positionMs
    ) {

        final long safePosition =
                Math.max(
                        0,
                        positionMs
                );

        mMainHandler.post(() -> {

            try {

                PlaybackView v =
                        view();

                if (v != null) {

                    v.setPositionMs(
                            safePosition
                    );
                }

            } catch (Exception ignored) {
            }
        });
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

        final String cleanVideoId =
                videoId.trim();

        /*
         * IMPORTANTE:
         *
         * PlaybackPresenter.openVideo()
         * modifica el estado de SmartTube y
         * arranca PlaybackView.
         *
         * Nunca lo ejecutamos directamente
         * desde el hilo del WebSocket.
         */
        mMainHandler.post(() -> {

            try {

                PlaybackPresenter p =
                        presenter();

                p.openVideo(
                        cleanVideoId
                );

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void next() {

        mMainHandler.post(() -> {

            try {

                presenter()
                        .onNextClicked();

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void previous() {

        mMainHandler.post(() -> {

            try {

                presenter()
                        .onPreviousClicked();

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void setVolume(
            float volume
    ) {

        final float safeVolume =
                Math.max(
                        0.0f,
                        Math.min(
                                1.0f,
                                volume
                        )
                );

        mMainHandler.post(() -> {

            try {

                PlaybackView v =
                        view();

                if (v != null) {

                    v.setVolume(
                            safeVolume
                    );
                }

            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public long getPositionMs() {

        /*
         * Esta función puede ser llamada desde
         * el hilo de sincronización.
         *
         * El PlaybackView debe consultarse de
         * forma segura.
         */
        try {

            PlaybackView v =
                    view();

            if (v == null) {
                return 0;
            }

            return Math.max(
                    0,
                    v.getPositionMs()
            );

        } catch (Exception ignored) {

            return 0;
        }
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

            if (presenter().getVideo() != null) {

                return presenter()
                        .getVideo()
                        .videoId;
            }

        } catch (Exception ignored) {
        }

        return null;
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
