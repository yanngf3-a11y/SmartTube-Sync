package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.ygsync.YgSyncCommand;
import com.liskovsoft.smartyoutubetv2.ygsync.YgSyncDiscoveryServer;
import com.liskovsoft.smartyoutubetv2.ygsync.YgSyncPlaybackBridge;
import com.liskovsoft.smartyoutubetv2.ygsync.YgSyncServer;

import java.net.Socket;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</a>.
 */
public class PlaybackActivity extends LeanbackActivity {

    private static final String TAG =
            PlaybackActivity.class.getSimpleName();

    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;

    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;

    private boolean gamepadTriggerPressed = false;

    private PlaybackFragment mPlaybackFragment;

    private YgSyncPlaybackBridge mYgSyncPlaybackBridge;

    private boolean mIsBackPressed;

    /**
     * YG Sync TCP network server.
     */
    private YgSyncServer mYgSyncServer;

    /**
     * YG Sync UDP discovery server.
     */
    private YgSyncDiscoveryServer mYgSyncDiscoveryServer;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_playback);

        findPlaybackFragment();

        startYgSyncServer();

        startYgSyncDiscoveryServer();

        Log.d(
                TAG,
                "YG Sync PlaybackActivity created. PlaybackFragment found: "
                        + (mPlaybackFragment != null)
        );
    }

    /**
     * Finds the active PlaybackFragment and creates the
     * YG Sync bridge.
     *
     * This method can be called more than once because the
     * Fragment may not be immediately available during the
     * first Activity lifecycle callback.
     */
    private boolean findPlaybackFragment() {

        Fragment fragment =
                getSupportFragmentManager()
                        .findFragmentByTag(
                                getString(R.string.playback_tag)
                        );

        if (fragment instanceof PlaybackFragment) {

            mPlaybackFragment =
                    (PlaybackFragment) fragment;

        } else {

            for (
                    Fragment candidate
                    : getSupportFragmentManager().getFragments()
            ) {

                if (candidate instanceof PlaybackFragment) {

                    mPlaybackFragment =
                            (PlaybackFragment) candidate;

                    break;
                }
            }
        }

        if (mPlaybackFragment != null) {

            if (mYgSyncPlaybackBridge == null) {

                mYgSyncPlaybackBridge =
                        new YgSyncPlaybackBridge(
                                mPlaybackFragment
                        );
            }

            Log.d(
                    TAG,
                    "YG Sync PlaybackFragment found: "
                            + mPlaybackFragment
            );

            return true;
        }

        Log.e(
                TAG,
                "YG Sync PlaybackFragment NOT found"
        );

        return false;
    }

    /**
     * Ensures that the PlaybackFragment and YG Sync bridge
     * are available before executing a command.
     */
    private boolean ensurePlaybackBridge() {

        if (
                mPlaybackFragment != null &&
                mYgSyncPlaybackBridge != null
        ) {

            return true;
        }

        Log.d(
                TAG,
                "YG Sync bridge not ready. Searching for PlaybackFragment..."
        );

        return findPlaybackFragment();
    }

    /**
     * Starts the YG Sync TCP server and connects
     * incoming commands to the SmartTube playback engine.
     */
    private void startYgSyncServer() {

        if (
                mYgSyncServer != null &&
                mYgSyncServer.isRunning()
        ) {
            return;
        }

        mYgSyncServer =
                new YgSyncServer(
                        YgSyncServer.DEFAULT_PORT,
                        new YgSyncServer.Listener() {

                            @Override
                            public void onConnected(
                                    Socket socket
                            ) {

                                Log.d(
                                        TAG,
                                        "YG Sync connected: "
                                                + socket.getInetAddress()
                                                .getHostAddress()
                                );
                            }

                            @Override
                            public void onCommand(
                                    String command,
                                    Socket socket
                            ) {

                                Log.d(
                                        TAG,
                                        "YG Sync command received: ["
                                                + command
                                                + "]"
                                );

                                if (command == null) {

                                    Log.e(
                                            TAG,
                                            "YG Sync received NULL command"
                                    );

                                    mYgSyncServer.send(
                                            socket,
                                            "ERROR|NULL_COMMAND"
                                    );

                                    return;
                                }

                                String commandName =
                                        getCommandName(command);

                                Log.d(
                                        TAG,
                                        "YG Sync command name: ["
                                                + commandName
                                                + "]"
                                );

                                if (
                                        YgSyncCommand.PING.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync responding PONG"
                                    );

                                    mYgSyncServer.send(
                                            socket,
                                            "PONG"
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.CONNECT.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync CONNECT received"
                                    );

                                    mYgSyncServer.send(
                                            socket,
                                            "CONNECTED"
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.DISCONNECT.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync DISCONNECT received"
                                    );

                                    mYgSyncServer.send(
                                            socket,
                                            "DISCONNECTED"
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.LOAD_VIDEO.equals(
                                                commandName
                                        )
                                ) {

                                    String videoId =
                                            getCommandPayload(command);

                                    Log.d(
                                            TAG,
                                            "YG Sync LOAD_VIDEO received. ID: "
                                                    + videoId
                                    );

                                    if (
                                            videoId == null ||
                                            videoId.trim().isEmpty()
                                    ) {

                                        Log.e(
                                                TAG,
                                                "YG Sync LOAD_VIDEO invalid video ID"
                                        );

                                        mYgSyncServer.send(
                                                socket,
                                                "ERROR|INVALID_VIDEO_ID"
                                        );

                                        return;
                                    }

                                    final String cleanVideoId =
                                            videoId.trim();

                                    runOnUiThread(
                                            () -> {

                                                Log.d(
                                                        TAG,
                                                        "YG Sync LOAD_VIDEO executing on UI thread. ID: "
                                                                + cleanVideoId
                                                );

                                                if (
                                                        !ensurePlaybackBridge()
                                                ) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync LOAD_VIDEO failed: PlaybackBridge unavailable"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAYER_NOT_READY"
                                                    );

                                                    return;
                                                }

                                                try {

                                                    mYgSyncPlaybackBridge
                                                            .loadVideo(
                                                                    cleanVideoId
                                                            );

                                                    Log.d(
                                                            TAG,
                                                            "YG Sync LOAD_VIDEO bridge.loadVideo executed successfully"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "OK|LOAD_VIDEO"
                                                    );

                                                } catch (Exception error) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync LOAD_VIDEO execution error: "
                                                                    + error.getMessage()
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|LOAD_VIDEO_FAILED"
                                                    );
                                                }
                                            }
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.PLAY.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync PLAY received"
                                    );

                                    runOnUiThread(
                                            () -> {

                                                Log.d(
                                                        TAG,
                                                        "YG Sync PLAY executing on UI thread"
                                                );

                                                if (
                                                        !ensurePlaybackBridge()
                                                ) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync PLAY failed: PlaybackBridge unavailable"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAYER_NOT_READY"
                                                    );

                                                    return;
                                                }

                                                try {

                                                    mYgSyncPlaybackBridge.play();

                                                    Log.d(
                                                            TAG,
                                                            "YG Sync PLAY executed successfully"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "OK|PLAY"
                                                    );

                                                } catch (Exception error) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync PLAY execution error: "
                                                                    + error.getMessage()
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAY_FAILED"
                                                    );
                                                }
                                            }
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.PAUSE.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync PAUSE received"
                                    );

                                    runOnUiThread(
                                            () -> {

                                                Log.d(
                                                        TAG,
                                                        "YG Sync PAUSE executing on UI thread"
                                                );

                                                if (
                                                        !ensurePlaybackBridge()
                                                ) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync PAUSE failed: PlaybackBridge unavailable"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAYER_NOT_READY"
                                                    );

                                                    return;
                                                }

                                                try {

                                                    mYgSyncPlaybackBridge.pause();

                                                    Log.d(
                                                            TAG,
                                                            "YG Sync PAUSE executed successfully"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "OK|PAUSE"
                                                    );

                                                } catch (Exception error) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync PAUSE execution error: "
                                                                    + error.getMessage()
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PAUSE_FAILED"
                                                    );
                                                }
                                            }
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.STOP.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync STOP received"
                                    );

                                    runOnUiThread(
                                            () -> {

                                                Log.d(
                                                        TAG,
                                                        "YG Sync STOP executing on UI thread"
                                                );

                                                if (
                                                        !ensurePlaybackBridge()
                                                ) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync STOP failed: PlaybackBridge unavailable"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAYER_NOT_READY"
                                                    );

                                                    return;
                                                }

                                                try {

                                                    mYgSyncPlaybackBridge.stop();

                                                    Log.d(
                                                            TAG,
                                                            "YG Sync STOP executed successfully"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "OK|STOP"
                                                    );

                                                } catch (Exception error) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync STOP execution error: "
                                                                    + error.getMessage()
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|STOP_FAILED"
                                                    );
                                                }
                                            }
                                    );

                                    return;
                                }

                                if (
                                        YgSyncCommand.SEEK.equals(
                                                commandName
                                        )
                                ) {

                                    String payload =
                                            getCommandPayload(command);

                                    Log.d(
                                            TAG,
                                            "YG Sync SEEK received: "
                                                    + payload
                                    );

                                    try {

                                        long positionMs =
                                                Long.parseLong(
                                                        payload
                                                );

                                        runOnUiThread(
                                                () -> {

                                                    if (
                                                            !ensurePlaybackBridge()
                                                    ) {

                                                        Log.e(
                                                                TAG,
                                                                "YG Sync SEEK failed: PlaybackBridge unavailable"
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "ERROR|PLAYER_NOT_READY"
                                                        );

                                                        return;
                                                    }

                                                    try {

                                                        mYgSyncPlaybackBridge
                                                                .seek(
                                                                        Math.max(
                                                                                0L,
                                                                                positionMs
                                                                        )
                                                                );

                                                        Log.d(
                                                                TAG,
                                                                "YG Sync SEEK executed successfully"
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "OK|SEEK"
                                                        );

                                                    } catch (Exception error) {

                                                        Log.e(
                                                                TAG,
                                                                "YG Sync SEEK execution error: "
                                                                        + error.getMessage()
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "ERROR|SEEK_FAILED"
                                                        );
                                                    }
                                                }
                                        );

                                    } catch (Exception error) {

                                        Log.e(
                                                TAG,
                                                "YG Sync invalid SEEK position: "
                                                        + payload
                                        );

                                        mYgSyncServer.send(
                                                socket,
                                                "ERROR|INVALID_POSITION"
                                        );
                                    }

                                    return;
                                }

                                if (
                                        YgSyncCommand.SET_VOLUME.equals(
                                                commandName
                                        )
                                ) {

                                    String payload =
                                            getCommandPayload(command);

                                    Log.d(
                                            TAG,
                                            "YG Sync SET_VOLUME received: "
                                                    + payload
                                    );

                                    try {

                                        float volume =
                                                Float.parseFloat(
                                                        payload
                                                );

                                        volume =
                                                Math.max(
                                                        0f,
                                                        Math.min(
                                                                1f,
                                                                volume
                                                        )
                                                );

                                        final float finalVolume =
                                                volume;

                                        runOnUiThread(
                                                () -> {

                                                    if (
                                                            !ensurePlaybackBridge()
                                                    ) {

                                                        Log.e(
                                                                TAG,
                                                                "YG Sync SET_VOLUME failed: PlaybackBridge unavailable"
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "ERROR|PLAYER_NOT_READY"
                                                        );

                                                        return;
                                                    }

                                                    try {

                                                        mYgSyncPlaybackBridge
                                                                .setVolume(
                                                                        finalVolume
                                                                );

                                                        Log.d(
                                                                TAG,
                                                                "YG Sync SET_VOLUME executed successfully"
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "OK|SET_VOLUME"
                                                        );

                                                    } catch (Exception error) {

                                                        Log.e(
                                                                TAG,
                                                                "YG Sync SET_VOLUME execution error: "
                                                                        + error.getMessage()
                                                        );

                                                        mYgSyncServer.send(
                                                                socket,
                                                                "ERROR|SET_VOLUME_FAILED"
                                                        );
                                                    }
                                                }
                                        );

                                    } catch (Exception error) {

                                        Log.e(
                                                TAG,
                                                "YG Sync invalid volume: "
                                                        + payload
                                        );

                                        mYgSyncServer.send(
                                                socket,
                                                "ERROR|INVALID_VOLUME"
                                        );
                                    }

                                    return;
                                }

                                if (
                                        YgSyncCommand.GET_STATUS.equals(
                                                commandName
                                        )
                                ) {

                                    Log.d(
                                            TAG,
                                            "YG Sync GET_STATUS received"
                                    );

                                    runOnUiThread(
                                            () -> {

                                                if (
                                                        !ensurePlaybackBridge()
                                                ) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync GET_STATUS failed: PlaybackBridge unavailable"
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|PLAYER_NOT_READY"
                                                    );

                                                    return;
                                                }

                                                try {

                                                    String videoId =
                                                            mYgSyncPlaybackBridge
                                                                    .getVideoId();

                                                    long position =
                                                            mYgSyncPlaybackBridge
                                                                    .getPosition();

                                                    long duration =
                                                            mYgSyncPlaybackBridge
                                                                    .getDuration();

                                                    boolean playing =
                                                            mYgSyncPlaybackBridge
                                                                    .isPlaying();

                                                    float volume =
                                                            mYgSyncPlaybackBridge
                                                                    .getVolume();

                                                    if (videoId == null) {
                                                        videoId = "";
                                                    }

                                                    String response =
                                                            "STATUS|"
                                                                    + videoId
                                                                    + "|"
                                                                    + position
                                                                    + "|"
                                                                    + duration
                                                                    + "|"
                                                                    + playing
                                                                    + "|"
                                                                    + volume;

                                                    Log.d(
                                                            TAG,
                                                            "YG Sync GET_STATUS response: "
                                                                    + response
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            response
                                                    );

                                                } catch (Exception error) {

                                                    Log.e(
                                                            TAG,
                                                            "YG Sync GET_STATUS execution error: "
                                                                    + error.getMessage()
                                                    );

                                                    mYgSyncServer.send(
                                                            socket,
                                                            "ERROR|STATUS_FAILED"
                                                    );
                                                }
                                            }
                                    );

                                    return;
                                }

                                Log.e(
                                        TAG,
                                        "YG Sync unknown command: "
                                                + command
                                );

                                mYgSyncServer.send(
                                        socket,
                                        "ERROR|UNKNOWN_COMMAND"
                                );
                            }

                            @Override
                            public void onDisconnected(
                                    Socket socket
                            ) {

                                Log.d(
                                        TAG,
                                        "YG Sync disconnected"
                                );
                            }

                            @Override
                            public void onError(
                                    Exception error
                            ) {

                                Log.e(
                                        TAG,
                                        "YG Sync server error: "
                                                + error.getMessage()
                                );
                            }
                        }
                );

        mYgSyncServer.start();

        Log.d(
                TAG,
                "YG Sync server started on port "
                        + mYgSyncServer.getPort()
        );
    }

    /**
     * Starts the YG Sync UDP discovery server.
     *
     * The Controller sends YG_SYNC_DISCOVER on UDP port 8766.
     * This server responds with the SmartTube receiver name
     * and TCP port used for commands.
     */
    private void startYgSyncDiscoveryServer() {

        if (
                mYgSyncDiscoveryServer != null &&
                mYgSyncDiscoveryServer.isRunning()
        ) {
            return;
        }

        mYgSyncDiscoveryServer =
                new YgSyncDiscoveryServer(this);

        mYgSyncDiscoveryServer.start();

        Log.d(
                TAG,
                "YG Sync discovery started on UDP port "
                        + mYgSyncDiscoveryServer.getPort()
        );
    }

    /**
     * Returns the command name before the first '|'.
     */
    private String getCommandName(
            String command
    ) {

        if (command == null) {
            return "";
        }

        String[] parts =
                command.split(
                        "\\|",
                        2
                );

        return parts[0].trim();
    }

    /**
     * Returns the payload after the first '|'.
     */
    private String getCommandPayload(
            String command
    ) {

        if (command == null) {
            return "";
        }

        String[] parts =
                command.split(
                        "\\|",
                        2
                );

        if (parts.length < 2) {
            return "";
        }

        return parts[1].trim();
    }

    /**
     * Stops the YG Sync TCP and UDP servers when the
     * playback Activity is really destroyed.
     */
    private void stopYgSyncServer() {

        if (mYgSyncServer != null) {

            mYgSyncServer.stop();

            mYgSyncServer = null;
        }

        if (mYgSyncDiscoveryServer != null) {

            mYgSyncDiscoveryServer.stop();

            mYgSyncDiscoveryServer = null;
        }

        mYgSyncPlaybackBridge = null;

        Log.d(
                TAG,
                "YG Sync TCP and UDP servers stopped"
        );
    }

    @Override
    protected void initTheme() {

        int playerThemeResId =
                MainUIData.instance(this)
                        .getColorScheme()
                        .playerThemeResId;

        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchKeyEvent(event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(
            MotionEvent event
    ) {

        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchGenericMotionEvent(event);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(
            int keyCode,
            KeyEvent event
    ) {

        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {

            mPlaybackFragment.skipToNext();

            return true;

        } else if (
                keyCode == KeyEvent.KEYCODE_BUTTON_L1
        ) {

            mPlaybackFragment.skipToPrevious();

            return true;

        } else if (
                keyCode == KeyEvent.KEYCODE_BUTTON_L2
        ) {

            mPlaybackFragment.rewind();

            return true;

        } else if (
                keyCode == KeyEvent.KEYCODE_BUTTON_R2
        ) {

            mPlaybackFragment.fastForward();

            return true;
        }

        return super.onKeyDown(
                keyCode,
                event
        );
    }

    @Override
    public boolean onGenericMotionEvent(
            MotionEvent event
    ) {

        // This method will handle gamepad events.

        if (
                event.getAxisValue(
                        MotionEvent.AXIS_LTRIGGER
                ) > GAMEPAD_TRIGGER_INTENSITY_ON
                        && !gamepadTriggerPressed
        ) {

            mPlaybackFragment.rewind();

            gamepadTriggerPressed = true;

        } else if (
                event.getAxisValue(
                        MotionEvent.AXIS_RTRIGGER
                ) > GAMEPAD_TRIGGER_INTENSITY_ON
                        && !gamepadTriggerPressed
        ) {

            mPlaybackFragment.fastForward();

            gamepadTriggerPressed = true;

        } else if (
                event.getAxisValue(
                        MotionEvent.AXIS_LTRIGGER
                ) < GAMEPAD_TRIGGER_INTENSITY_OFF
                        && event.getAxisValue(
                        MotionEvent.AXIS_RTRIGGER
                ) < GAMEPAD_TRIGGER_INTENSITY_OFF
        ) {

            gamepadTriggerPressed = false;

        } else if (
                (event.getSource()
                        & InputDevice.SOURCE_CLASS_POINTER) != 0
                        && event.getAction()
                        == MotionEvent.ACTION_SCROLL
        ) {

            // mouse wheel handling

            Utils.volumeUp(
                    this,
                    getPlaybackView(),
                    event.getAxisValue(
                            MotionEvent.AXIS_VSCROLL
                    ) < 0.0f
            );

            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    // For N devices that support it, not "officially"
    // More: https://medium.com/s23nyc-tech/drop-in-android-video-exoplayer2-with-picture-in-picture-e2d4f8c1eb30
    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {

        if (Helpers.isPictureInPictureSupported(this)) {

            if (wannaEnterToPip()) {

                Log.d(
                        TAG,
                        "Entering PIP mode..."
                );

                try {

                    if (Build.VERSION.SDK_INT >= 26) {

                        PictureInPictureParams.Builder params =
                                new PictureInPictureParams.Builder();

                        enterPictureInPictureMode(
                                params.build()
                        );

                    } else {

                        enterPictureInPictureMode();
                    }

                } catch (Exception e) {

                    // Device doesn't support picture-in-picture mode

                    Log.e(
                            TAG,
                            e.getMessage()
                    );
                }
            }
        }
    }

    /**
     * BACK pressed, PIP player's button pressed
     */
    @Override
    public void finish() {

        Log.d(
                TAG,
                "Finishing activity..."
        );

        //if (isBackgroundBackEnabled()) {
        //    mPlaybackFragment.blockEngine(true);
        //}

        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        // NOTE: block back button for PIP.
        // User pressed PIP button in the player.
        if (!skipPip()) {

            enterPipMode();

        }

        if (doNotDestroy() && !skipPip()) {

            mPlaybackFragment.blockEngine(true);

            // Ensure to opening this activity when the user is returning to the app
            getViewManager().blockTop(this);

            getViewManager().startParentView(this);

        } else {

            if (
                    getPlayerTweaksData()
                            .isKeepFinishedActivityEnabled()
            ) {

                //moveTaskToBack(true); // Don't do this or you'll have problems when player overlaps other apps (e.g. casting)

                getViewManager().startParentView(this);

                // Player with TextureView keeps running in background because onStop() fired with huge delay (~5sec).
                mPlaybackFragment.maybeReleasePlayer();

            } else {

                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {

        stopYgSyncServer();

        super.finishReally();

        mPlaybackFragment.onFinish();
    }

    @Override
    protected void onPause() {

        boolean hasDialogBug =
                AppDialogPresenter.instance(this)
                        .isDialogShown()
                        && Build.VERSION.SDK_INT <= 23;

        boolean isScreenOff =
                getPlayerData().getBackgroundMode()
                        != PlayerData.BACKGROUND_MODE_DEFAULT
                        && Utils.isHardScreenOff(this);

        if (hasDialogBug || isScreenOff) {
            mPlaybackFragment.blockEngine(true);
        }

        // Run the code before the contained fragment
        super.onPause();
    }

    @Override
    public void onBackPressed() {

        mIsBackPressed = true;

        super.onBackPressed();
    }

    @Override
    protected void onResume() {

        mIsBackPressed = false;

        super.onResume();
    }

    @SuppressWarnings("deprecation")
    private void enterBackgroundPlayMode() {

        if (
                Build.VERSION.SDK_INT >= 21
                        && Build.VERSION.SDK_INT < 26
        ) {

            if (Build.VERSION.SDK_INT == 21) {

                // Playback pause fix?

                mPlaybackFragment.showOverlay(true);
            }

            if (mPlaybackFragment.isPlaying()) {

                // Argument equals true to notify the system that the activity wishes to be visible behind other translucent activities

                if (!requestVisibleBehind(true)) {

                    // App-specific method to stop playback and release resources because call to requestVisibleBehind(true) failed

                    mPlaybackFragment.onDestroy();
                }

            } else {

                // Argument equals false because the activity is not playing

                requestVisibleBehind(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onVisibleBehindCanceled() {

        // App-specific method to stop playback and release resources

        mPlaybackFragment.onDestroy();

        super.onVisibleBehindCanceled();
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode
    ) {

        super.onPictureInPictureModeChanged(
                isInPictureInPictureMode
        );

        mPlaybackFragment.onPIPChanged(
                isInPictureInPictureMode
        );
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {

        // Check that user not open dialog/search activity instead of really leaving the activity
        // Activity may be overlapped by the dialog, back is pressed or new view started
        // Activity may be overlapped by the dialog, back is pressed or new view started
        if (
                mIsBackPressed
                        || isFinishing()
                        || getViewManager().isNewViewPending()
                        || getGeneralData().getBackgroundPlaybackShortcut()
                        == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK
        ) {
            return;
        }

        switch (
                getPlayerData().getBackgroundMode()
        ) {

            case PlayerData.BACKGROUND_MODE_PLAY_BEHIND:

                enterBackgroundPlayMode();

                // Do we need to do something additional when running Play Behind?

                break;

            case PlayerData.BACKGROUND_MODE_PIP:

                enterPipMode();

                if (doNotDestroy()) {

                    mPlaybackFragment.blockEngine(true);

                    // Ensure to opening this activity when the user will return to the app

                    getViewManager().blockTop(this);

                    // Enable collapse app to Home launcher
                    //getViewManager().enableMoveToBack(true);
                }

                break;

            case PlayerData.BACKGROUND_MODE_SOUND:

                if (doNotDestroy()) {

                    // Ensure to continue a playback

                    mPlaybackFragment.blockEngine(true);

                    getViewManager().blockTop(this);

                    //getViewManager().enableMoveToBack(true);
                }

                break;
        }
    }

    public boolean isInPipMode() {

        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        return isInPictureInPictureMode();
    }

    public PlaybackView getPlaybackView() {

        return mPlaybackFragment;
    }

    private boolean skipPip() {

        return mIsBackPressed
                && getGeneralData()
                .getBackgroundPlaybackShortcut()
                == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    private boolean isEngineBlocked() {

        return mPlaybackFragment != null
                && mPlaybackFragment.isEngineBlocked();
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {

        //return mPlaybackFragment != null && mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_PIP && !isInPictureInPictureMode();
        //return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked() && !isInPictureInPictureMode();

        boolean isPip =
                getPlayerData().getBackgroundMode()
                        == PlayerData.BACKGROUND_MODE_PIP
                        || isEngineBlocked();

        return isPip
                && !isInPictureInPictureMode();
    }

    private boolean doNotDestroy() {

        sIsInPipMode = isInPipMode();

        //return sIsInPipMode || mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND;
        //return sIsInPipMode || mPlaybackFragment.isEngineBlocked();

        boolean isBackground =
                getPlayerData().getBackgroundMode()
                        == PlayerData.BACKGROUND_MODE_SOUND
                        || isEngineBlocked();

        return sIsInPipMode
                || isBackground;
    }

    //private boolean isBackgroundBackEnabled() {
    //    return getGeneralData().getBackgroundPlaybackShortcut() == PlayerData.BACKGROUND_PLAYBACK_SHORTCUT_BACK ||
    //            getGeneralData().getBackgroundPlaybackShortcut() == PlayerData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK;
    //}
                }
