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

                /*
                 * STOP:
                 * pausa y devuelve el reproductor
                 * al inicio del contenido.
                 */
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
                        message.payload
                                .optString(
                                        "videoId",
                                        ""
                                )
                                .trim();

                if (!videoId.isEmpty()) {

                    player.openVideo(
                            videoId
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

                /*
                 * El volumen se recibe en rango 0.0 - 1.0.
                 *
                 * SyncPlayerBridge actualmente no expone
                 * setVolume(), por lo que este comando queda
                 * reservado hasta ampliar la interfaz.
                 */
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

            default:

                /*
                 * Tipo desconocido:
                 * se ignora sin provocar un crash.
                 */
                break;
        }

    } catch (Exception ignored) {

        /*
         * Un comando malformado nunca debe tumbar
         * el servicio de sincronización.
         */
    }
}

}
