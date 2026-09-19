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

                /*
                 * Vigilancia por si el video se queda "cargando"
                 * para siempre en esta TV puntual (pasaba en una
                 * pantalla específica: terminaba quedando trabada
                 * y había que cerrar y reabrir el Controller para
                 * que reaccionara). Si sigue sin destrabarse sola,
                 * esto reintenta el openVideo() por su cuenta.
                 */
                scheduleStallWatchdog(
                        cleanVideoId,
                        0,
                        0
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

                scheduleStallWatchdog(
                        cleanVideoId,
                        0,
                        0
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
    public void setSpeed(
            float speed
    ) {

        final float safeSpeed =
                Math.max(
                        0.85f,
                        Math.min(
                                1.15f,
                                speed
                        )
                );

        mMainHandler.post(() -> {

            try {

                PlaybackView v =
                        view();

                if (v != null) {

                    v.setSpeed(
                            safeSpeed
                    );
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error en setSpeed()",
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

    /**
     * Cuántas veces revisamos si el video sigue "cargando" antes
     * de reintentar, cada cuánto, y cuántos reintentos totales
     * dejamos hacer antes de rendirnos del todo para ese video
     * (para no quedar reintentando para siempre si de verdad hay
     * un problema de red más de fondo).
     */
    private static final long STALL_CHECK_INTERVAL_MS = 3000L;

    private static final int STALL_CHECKS_BEFORE_RETRY = 4; // 4 x 3s = 12s

    private static final int STALL_MAX_RETRIES = 5;

    /**
     * Vigila que el video pedido no se quede "cargando" para
     * siempre. Antes, si eso pasaba en una TV puntual (red más
     * lenta, algo raro del momento), la pantalla quedaba trabada
     * hasta que el usuario cerraba y volvía a abrir el Controller
     * a mano. Ahora, si después de 12 segundos sigue sin destrabar
     * solo, se reintenta el openVideo() automáticamente (hasta
     * STALL_MAX_RETRIES veces).
     */
    private void scheduleStallWatchdog(
            String videoId,
            int checkCount,
            int retryCount
    ) {

        mMainHandler.postDelayed(
                () -> {

                    try {

                        /*
                         * Mientras tanto se pidió otro video: este
                         * watchdog quedó obsoleto, no hacemos nada.
                         */
                        if (
                                mRequestedVideoId == null ||
                                !mRequestedVideoId.equals(
                                        videoId
                                )
                        ) {
                            return;
                        }

                        PlaybackView v =
                                view();

                        boolean stillStuck =
                                v == null ||
                                v.isLoading() ||
                                !v.containsMedia();

                        if (!stillStuck) {
                            /*
                             * Se destrabó solo, no hace falta
                             * nada más.
                             */
                            return;
                        }

                        if (
                                checkCount
                                        < STALL_CHECKS_BEFORE_RETRY
                        ) {

                            scheduleStallWatchdog(
                                    videoId,
                                    checkCount + 1,
                                    retryCount
                            );

                            return;
                        }

                        if (
                                retryCount
                                        >= STALL_MAX_RETRIES
                        ) {

                            Log.e(
                                    TAG,
                                    "YG SYNC: video "
                                            + videoId
                                            + " sigue trabado tras "
                                            + STALL_MAX_RETRIES
                                            + " reintentos, salto al"
                                            + " siguiente"
                            );

                            showDiagnostic(
                                    "YG SYNC — VIDEO TRABADO, "
                                            + "SALTANDO AL "
                                            + "SIGUIENTE: "
                                            + videoId
                            );

                            /*
                             * Reintentar el mismo openVideo() ya no
                             * sirve: si después de 5 intentos sigue
                             * sin arrancar, lo más probable es que
                             * ESE video puntual tenga un problema
                             * en esta TV (no de red/timing). Antes
                             * acá nos rendíamos en silencio y la
                             * pantalla quedaba trabada para
                             * siempre. Saltar al siguiente evita
                             * que esa TV se quede colgada
                             * indefinidamente.
                             */
                            try {

                                presenter()
                                        .onNextClicked();

                            } catch (Exception skipError) {

                                Log.e(
                                        TAG,
                                        "Error saltando al "
                                                + "siguiente tras "
                                                + "video trabado",
                                        skipError
                                );
                            }

                            return;
                        }

                        Log.e(
                                TAG,
                                "YG SYNC: video "
                                        + videoId
                                        + " sigue cargando, "
                                        + "reintentando openVideo() ("
                                        + (retryCount + 1)
                                        + "/"
                                        + STALL_MAX_RETRIES
                                        + ")"
                        );

                        showDiagnostic(
                                "YG SYNC — VIDEO TRABADO, "
                                        + "REINTENTANDO: "
                                        + videoId
                        );

                        presenter().openVideo(
                                videoId
                        );

                        scheduleStallWatchdog(
                                videoId,
                                0,
                                retryCount + 1
                        );

                    } catch (Exception e) {

                        Log.e(
                                TAG,
                                "Error en scheduleStallWatchdog()",
                                e
                        );
                    }
                },
                STALL_CHECK_INTERVAL_MS
        );
    }

    private void showDiagnostic(
            String message
    ) {

        try {

            android.widget.Toast.makeText(
                    mContext,
                    message,
                    android.widget.Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error mostrando diagnóstico: "
                            + e.getMessage()
            );
        }
    }
}
