package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

public class SyncPlaybackBridge implements SyncPlayerBridge {

    private static final String TAG =
            "SyncPlaybackBridge";

    private final Context mContext;

    private final Handler mMainHandler =
            new Handler(Looper.getMainLooper());

    /*
     * Último vídeo solicitado por YG Sync.
     *
     * Se registra ANTES de enviar openVideo() al
     * PlaybackPresenter porque openVideo() es asíncrono.
     */
    private volatile String mRequestedVideoId;

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

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en play()",
                        e
                );
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

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en pause()",
                        e
                );
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

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en seekTo()",
                        e
                );
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
            Log.w(
                    TAG,
                    "openVideo() recibió videoId vacío"
            );

            return;
        }

        final String cleanVideoId =
                videoId.trim();

        /*
         * MUY IMPORTANTE:
         *
         * Registramos inmediatamente el vídeo solicitado.
         *
         * El WebSocket puede preguntar getVideoId()
         * antes de que PlaybackPresenter termine de
         * actualizar getVideo().
         */
        mRequestedVideoId =
                cleanVideoId;

        Log.d(
                TAG,
                "OPEN solicitado: "
                        + cleanVideoId
        );

        mMainHandler.post(() -> {

            try {

                PlaybackPresenter p =
                        presenter();

                Log.d(
                        TAG,
                        "Ejecutando PlaybackPresenter.openVideo(): "
                                + cleanVideoId
                );

                p.openVideo(
                        cleanVideoId
                );

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error ejecutando openVideo(): "
                                + cleanVideoId,
                        e
                );
            }
        });
    }

    @Override
    public void next() {

        mMainHandler.post(() -> {

            try {

                presenter()
                        .onNextClicked();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en next()",
                        e
                );
            }
        });
    }

    @Override
    public void previous() {

        mMainHandler.post(() -> {

            try {

                presenter()
                        .onPreviousClicked();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en previous()",
                        e
                );
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

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en setVolume()",
                        e
                );
            }
        });
    }

    @Override
    public long getPositionMs() {

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

        } catch (Exception e) {

            return 0;
        }
    }

    @Override
    public boolean isPlaying() {

        try {

            return presenter()
                    .isPlaying();

        } catch (Exception e) {

            return false;
        }
    }

    @Override
    public String getVideoId() {

        /*
         * Primero intentamos obtener el ID real
         * que SmartTube ya tiene cargado.
         */
        try {

            PlaybackPresenter p =
                    presenter();

            if (p.getVideo() != null) {

                String actualVideoId =
                        p.getVideo().videoId;

                if (
                        actualVideoId != null &&
                        !actualVideoId.trim().isEmpty()
                ) {

                    actualVideoId =
                            actualVideoId.trim();

                    /*
                     * El PlaybackPresenter ya confirmó
                     * realmente el vídeo.
                     */
                    if (
                            mRequestedVideoId != null &&
                            mRequestedVideoId.equals(
                                    actualVideoId
                            )
                    ) {

                        Log.d(
                                TAG,
                                "VIDEO CONFIRMADO: "
                                        + actualVideoId
                        );

                        return actualVideoId;
                    }

                    return actualVideoId;
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error obteniendo video real",
                    e
            );
        }

        /*
         * Si PlaybackPresenter todavía no actualizó
         * getVideo(), devolvemos el último vídeo solicitado.
         *
         * Esto permite que el sistema READY no dependa
         * exclusivamente de la actualización interna
         * del Presenter.
         */
        String requested =
                mRequestedVideoId;

        if (
                requested != null &&
                !requested.trim().isEmpty()
        ) {

            Log.d(
                    TAG,
                    "VIDEO solicitado todavía no reflejado "
                            + "en Presenter; usando solicitado: "
                            + requested
            );

            return requested;
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

        } catch (Exception e) {

            return 0.0f;
        }
    }
}
