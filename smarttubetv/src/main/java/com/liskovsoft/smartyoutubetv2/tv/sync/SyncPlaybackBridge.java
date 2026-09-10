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

            Log.e(
                    TAG,
                    "OPEN rechazado: videoId vacío"
            );

            return;
        }

        final String cleanVideoId =
                videoId.trim();

        mRequestedVideoId =
                cleanVideoId;

        Log.d(
                TAG,
                "================================"
        );

        Log.d(
                TAG,
                "YG SYNC OPEN RECIBIDO"
        );

        Log.d(
                TAG,
                "videoId="
                        + cleanVideoId
        );

        /*
         * Toda interacción con PlaybackPresenter
         * se ejecuta en el hilo principal.
         */
        mMainHandler.post(() -> {

            try {

                PlaybackPresenter p =
                        presenter();

                Log.d(
                        TAG,
                        "PlaybackPresenter="
                                + p
                );

                /*
                 * Estado anterior.
                 */
                try {

                    if (p.getVideo() != null) {

                        Log.d(
                                TAG,
                                "video ANTES="
                                        + p.getVideo().videoId
                        );

                    } else {

                        Log.d(
                                TAG,
                                "video ANTES=null"
                        );
                    }

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "No se pudo obtener video ANTES",
                            e
                    );
                }

                /*
                 * ESTA ES LA LLAMADA REAL QUE DEBE
                 * CAMBIAR EL VIDEO EN SMARTTUBE.
                 */
                Log.d(
                        TAG,
                        "LLAMANDO PlaybackPresenter.openVideo("
                                + cleanVideoId
                                + ")"
                );

                p.openVideo(
                        cleanVideoId
                );

                Log.d(
                        TAG,
                        "PlaybackPresenter.openVideo() terminó"
                );

                /*
                 * Estado inmediatamente después.
                 */
                try {

                    if (p.getVideo() != null) {

                        Log.d(
                                TAG,
                                "video DESPUES="
                                        + p.getVideo().videoId
                        );

                    } else {

                        Log.d(
                                TAG,
                                "video DESPUES=null"
                        );
                    }

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "No se pudo obtener video DESPUES",
                            e
                    );
                }

                Log.d(
                        TAG,
                        "================================"
                );

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "================================",
                        e
                );

                Log.e(
                        TAG,
                        "YG SYNC ERROR EN openVideo()"
                                + " videoId="
                                + cleanVideoId,
                        e
                );

                Log.e(
                        TAG,
                        "================================"
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

                    return actualVideoId.trim();
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error obteniendo videoId real",
                    e
            );
        }

        /*
         * Solamente informamos el ID solicitado si
         * SmartTube todavía no expuso uno real.
         *
         * Esto mantiene el diagnóstico compatible
         * con el servidor actual.
         */
        if (
                mRequestedVideoId != null &&
                !mRequestedVideoId.trim().isEmpty()
        ) {

            return mRequestedVideoId;
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
