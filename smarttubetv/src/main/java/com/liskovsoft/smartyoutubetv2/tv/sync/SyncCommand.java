package com.liskovsoft.smartyoutubetv2.tv.sync;

public final class SyncCommand {
    private static final long SEEK_CORRECTION_THRESHOLD_MS = 250;

    private SyncCommand() {}

    public static void execute(SyncMessage message, SyncPlayerBridge player) {
        if (message == null || player == null) {
            return;
        }

        try {
            switch (message.type) {
                case "play":
                    player.play();
                    break;
                case "pause":
                    player.pause();
                    break;
                case "seek":
                    player.seekTo(message.payload.optLong("positionMs", 0));
                    break;
                case "open":
                    String videoId = message.payload.optString("videoId", "");
                    if (!videoId.isEmpty()) {
                        player.openVideo(videoId);
                    }
                    break;
                case "next":
                    player.next();
                    break;
                case "previous":
                    player.previous();
                    break;
                case "sync":
                    long target = message.payload.optLong("targetPositionMs", -1);
                    long sentAt = message.payload.optLong("serverTimestampMs", message.timestamp);
                    if (target >= 0) {
                        long estimated = target + Math.max(0, System.currentTimeMillis() - sentAt);
                        if (Math.abs(player.getPositionMs() - estimated) > SEEK_CORRECTION_THRESHOLD_MS) {
                            player.seekTo(estimated);
                        }
                    }
                    break;
                default:
                    // Tipo desconocido: se ignora sin crashear
                    break;
            }
        } catch (Exception ignored) {
            // Nunca dejar que un comando malformado tumbe el servicio
        }
    }
}
