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
    public void prepareVideo(
            String videoId
    ) {

        if (
                videoId == null ||
                videoId.trim().isEmpty()
        ) {

            Log.e(
                    TAG,
                    "PREPARE rechazado: videoId vacío"
            );

            return;
        }

        final String cleanVideoId =
                videoId.trim();

        mRequestedVideoId =
                cleanVideoId;

        Log.d(
                TAG,
                "YG SYNC PREPARE RECIBIDO videoId="
                        + cleanVideoId
        );

        mMainHandler.post(() -> {

            try {

                PlaybackPresenter p =
                        presenter();

                p.openVideo(
                        cleanVideoId
                );

                /*
                 * A diferencia de openVideo(), acá no dejamos
                 * que arranque solo: lo pausamos apenas carga.
                 * El "play" real llega después, con "playAt"
                 * (Fase 3.3), al mismo tiempo en todas las TVs.
                 */
                PlaybackView v =
                        view();

                if (v != null) {

                    v.setPlayWhenReady(
                            false
                    );
                }

                Log.d(
                        TAG,
                        "YG SYNC PREPARE: video cargado en pausa "
                                + cleanVideoId
                );

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "YG SYNC ERROR EN prepareVideo() videoId="
                                + cleanVideoId,
                        e
                );
            }
        });
    }

    /*
     * Cuántas veces reintentamos playAt() si la vista del
     * reproductor todavía no está lista, y cada cuánto. 20 x 150ms
     * = 3 segundos de margen extra antes de rendirnos. Antes, si
     * view() daba null en el instante exacto de playAt() (algo
     * lento en esa TV, openVideo() todavía en transición), la
     * pantalla quedaba congelada para siempre: se abandonaba en
     * silencio y nadie lo volvía a intentar.
     */
    private static final int PLAY_AT_MAX_RETRIES = 20;

    private static final long PLAY_AT_RETRY_DELAY_MS = 150L;

    @Override
    public void playAt(
            long timestampMs
    ) {

        final long delayMs =
                Math.max(
                        0,
                        timestampMs
                                - System.currentTimeMillis()
                );

        Log.d(
                TAG,
                "YG SYNC PLAY_AT recibido, arranca en "
                        + delayMs
                        + "ms"
        );

        mMainHandler.postDelayed(
                () -> attemptPlayAt(0),
                delayMs
        );
    }

    private void attemptPlayAt(
            int attempt
    ) {

        try {

            PlaybackView v =
                    view();

            if (v != null) {

                v.setPlayWhenReady(
                        true
                );

                Log.d(
                        TAG,
                        "YG SYNC PLAY_AT: arrancó (intento "
                                + attempt
                                + ")"
                );

                return;
            }

            if (attempt < PLAY_AT_MAX_RETRIES) {

                Log.d(
                        TAG,
                        "YG SYNC PLAY_AT: vista no lista, "
                                + "reintento "
                                + (attempt + 1)
                                + "/"
                                + PLAY_AT_MAX_RETRIES
                );

                mMainHandler.postDelayed(
                        () -> attemptPlayAt(attempt + 1),
                        PLAY_AT_RETRY_DELAY_MS
                );

            } else {

                Log.e(
                        TAG,
                        "YG SYNC PLAY_AT: se agotaron los "
                                + "reintentos, la pantalla puede "
                                + "haber quedado congelada"
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error en playAt()",
                    e
            );
        }
    }

    @Override
    public boolean isReadyToPlay(
            String videoId
    ) {

        try {

            if (
                    videoId == null ||
                    videoId.trim().isEmpty()
            ) {
                return false;
            }

            String current =
                    getVideoId();

            if (
                    current == null ||
                    !current.trim().equals(
                            videoId.trim()
                    )
            ) {
                return false;
            }

            PlaybackView v =
                    view();

            if (v == null) {
                return false;
            }

            /*
             * No alcanza con que el ID coincida: hay que
             * esperar a que el motor termine de cargar
             * (isLoading() == false) y realmente tenga medios
             * cargados. Antes "ready" se mandaba apenas
             * coincidía el videoId, que pasa casi al instante
             * y mucho antes de que el video pueda arrancar sin
             * cortes — eso era la causa real de que unas TVs
             * arrancaran 1-2 segundos antes que otras aunque
             * "playAt" les llegara al mismo tiempo a todas.
             */
            if (!v.isEngineInitialized()) {
                return false;
            }

            if (v.isLoading()) {
                return false;
            }

            return v.containsMedia();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error en isReadyToPlay()",
                    e
            );

            return false;
        }
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
