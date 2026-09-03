package com.liskovsoft.smartyoutubetv2.ygsync;

import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

public class YgSyncPlaybackBridge {

    private final PlaybackFragment playbackFragment;

    public YgSyncPlaybackBridge(
            PlaybackFragment playbackFragment
    ) {
        this.playbackFragment = playbackFragment;
    }

    public void loadVideo(String videoId) {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncLoadVideo(videoId);
    }

    public void play() {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncPlay();
    }

    public void pause() {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncPause();
    }

    public void stop() {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncStop();
    }

    public void seek(long positionMs) {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncSeek(positionMs);
    }

    public void setVolume(float volume) {

        if (playbackFragment == null) {
            return;
        }

        playbackFragment.ygSyncSetVolume(volume);
    }

    public long getPosition() {

        if (playbackFragment == null) {
            return 0;
        }

        return playbackFragment.getPositionMs();
    }

    public long getDuration() {

        if (playbackFragment == null) {
            return 0;
        }

        return playbackFragment.getDurationMs();
    }

    public boolean isPlaying() {

        return playbackFragment != null
                && playbackFragment.isPlaying();
    }

    public float getVolume() {

        if (playbackFragment == null) {
            return 0f;
        }

        return playbackFragment.getVolume();
    }

    public String getVideoId() {

        if (
                playbackFragment == null
                        || playbackFragment.getVideo() == null
        ) {
            return null;
        }

        return playbackFragment.getVideo().videoId;
    }
}
