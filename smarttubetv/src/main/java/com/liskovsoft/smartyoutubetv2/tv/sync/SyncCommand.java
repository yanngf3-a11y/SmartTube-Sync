package com.liskovsoft.smartyoutubetv2.tv.sync;

public final class SyncCommand {

    private static final long SEEK_CORRECTION_THRESHOLD_MS = 250;

    private SyncCommand() {
    }

    public static void execute(
            SyncMessage message,
            SyncPlayerBridge player
    ) {

        if (
                message == null ||
                player == null
        ) {
            return;
        }

        try {

            String type =
                    message.type == null
                            ? ""
                            : message.type.trim();

            switch (type) {

                case "play":

                    player.play();

                    break;

                case "pause":

                    player.pause();

                    break;

                case "stop":

                    player.pause();
                    player.seekTo(0);

                    break;

                case "seek": {

                    long positionMs =
                            Math.max(
                                    0,
                                    message.payload.optLong(
                                            "positionMs",
                                            0
                                    )
                            );

                    player.seekTo(
                            positionMs
                    );

                    break;
                }

                case "open": {

                    String videoId =
                            message.payload.optString(
                                    "videoId",
                                    ""
                            ).trim();

                    if (!videoId.isEmpty()) {

                        player.openVideo(
                                videoId
                        );
                    }

                    break;
                }

                case "prepare": {

                    String videoId =
                            message.payload.optString(
                                    "videoId",
                                    ""
                            ).trim();

                    if (!videoId.isEmpty()) {

                        player.prepareVideo(
                                videoId
                        );
                    }

                    break;
                }

                case "playAt": {

                    long playAtMs =
                            message.payload.optLong(
                                    "playAtMs",
                                    -1
                            );

                    if (playAtMs > 0) {

                        player.playAt(
                                playAtMs
                        );
                    }

                    break;
                }

                case "next":

                    player.next();

                    break;

                case "previous":

                    player.previous();

                    break;

                case "setVolume": {

                    float volume =
                            (float) message.payload.optDouble(
                                    "volume",
                                    1.0
                            );

                    volume =
                            Math.max(
                                    0.0f,
                                    Math.min(
                                            1.0f,
                                            volume
                                    )
                            );

                    player.setVolume(
                            volume
                    );

                    break;
                }

                case "setSpeed": {

                    /*
                     * Corrección "suave" de drift: en vez de un
                     * seek (que siempre genera un corte/recongelado
                     * visible), se acelera o frena levemente la
                     * reproducción (ej. 1.03x / 0.97x) hasta que la
                     * pantalla se empareja sola con la referencia,
                     * y después se vuelve a 1.0x. El Controller
                     * decide cuándo usar esto en vez de "sync".
                     */
                    float speed =
                            (float) message.payload.optDouble(
                                    "speed",
                                    1.0
                            );

                    speed =
                            Math.max(
                                    0.85f,
                                    Math.min(
                                            1.15f,
                                            speed
                                    )
                            );

                    player.setSpeed(
                            speed
                    );

                    break;
                }

                case "sync": {

                    long targetPositionMs =
                            message.payload.optLong(
                                    "targetPositionMs",
                                    -1
                            );

                    long serverTimestampMs =
                            message.payload.optLong(
                                    "serverTimestampMs",
                                    message.timestamp
                            );

                    if (
                            targetPositionMs >= 0
                    ) {

                        long elapsedMs =
                                Math.max(
                                        0,
                                        System.currentTimeMillis()
                                                - serverTimestampMs
                                );

                        long estimatedPositionMs =
                                targetPositionMs
                                        + elapsedMs;

                        long currentPositionMs =
                                player.getPositionMs();

                        long differenceMs =
                                Math.abs(
                                        currentPositionMs
                                                - estimatedPositionMs
                                );

                        if (
                                differenceMs
                                        > SEEK_CORRECTION_THRESHOLD_MS
                        ) {

                            player.seekTo(
                                    estimatedPositionMs
                            );
                        }
                    }

                    break;
                }

                case "getStatus":
                case "status":

                    break;

                default:

                    break;
            }

        } catch (Exception ignored) {
            /*
             * Un comando malformado nunca debe
             * tumbar el servicio de sincronización.
             */
        }
    }
    }
