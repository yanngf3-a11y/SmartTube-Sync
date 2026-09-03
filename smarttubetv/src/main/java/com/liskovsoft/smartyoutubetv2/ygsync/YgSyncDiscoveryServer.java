package com.liskovsoft.smartyoutubetv2.ygsync;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * YG Sync UDP discovery server.
 *
 * Listens on UDP port 8766 and responds to:
 *
 * YG_SYNC_DISCOVER
 *
 * with:
 *
 * YG_SYNC_RECEIVER|YG Sync SmartTube|8765
 */
public class YgSyncDiscoveryServer {

    public static final int DEFAULT_PORT = 8766;
    public static final int TCP_PORT = 8765;

    private static final String TAG =
            YgSyncDiscoveryServer.class.getSimpleName();

    private static final String DISCOVERY_REQUEST =
            "YG_SYNC_DISCOVER";

    private static final String RECEIVER_NAME =
            "YG Sync SmartTube";

    private final Context context;

    private DatagramSocket socket;
    private Thread workerThread;

    private volatile boolean running;

    private WifiManager.MulticastLock multicastLock;

    public YgSyncDiscoveryServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start() {

        if (running) {
            return;
        }

        running = true;

        acquireMulticastLock();

        workerThread =
                new Thread(
                        this::runServer,
                        "YG-Sync-Discovery"
                );

        workerThread.start();

        Log.d(
                TAG,
                "YG Sync discovery server started on UDP "
                        + DEFAULT_PORT
        );
    }

    private void runServer() {

        try {

            socket =
                    new DatagramSocket(
                            DEFAULT_PORT,
                            InetAddress.getByName("0.0.0.0")
                    );

            socket.setBroadcast(true);
            socket.setReuseAddress(true);

            byte[] buffer =
                    new byte[1024];

            while (running) {

                DatagramPacket packet =
                        new DatagramPacket(
                                buffer,
                                buffer.length
                        );

                try {

                    socket.receive(packet);

                } catch (IOException error) {

                    if (running) {

                        Log.e(
                                TAG,
                                "Discovery receive error: "
                                        + error.getMessage()
                        );
                    }

                    break;
                }

                if (!running) {
                    break;
                }

                String request =
                        new String(
                                packet.getData(),
                                packet.getOffset(),
                                packet.getLength(),
                                StandardCharsets.UTF_8
                        ).trim();

                Log.d(
                        TAG,
                        "Discovery request from "
                                + packet.getAddress().getHostAddress()
                                + ": "
                                + request
                );

                if (!DISCOVERY_REQUEST.equals(request)) {
                    continue;
                }

                String response =
                        "YG_SYNC_RECEIVER|"
                                + RECEIVER_NAME
                                + "|"
                                + TCP_PORT;

                byte[] responseData =
                        response.getBytes(
                                StandardCharsets.UTF_8
                        );

                DatagramPacket responsePacket =
                        new DatagramPacket(
                                responseData,
                                responseData.length,
                                packet.getAddress(),
                                packet.getPort()
                        );

                try {

                    socket.send(responsePacket);

                    Log.d(
                            TAG,
                            "Discovery response sent to "
                                    + packet.getAddress()
                                    .getHostAddress()
                                    + ":"
                                    + packet.getPort()
                    );

                } catch (IOException error) {

                    if (running) {

                        Log.e(
                                TAG,
                                "Discovery send error: "
                                        + error.getMessage()
                        );
                    }
                }
            }

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "YG Sync discovery server error: "
                            + error.getMessage()
            );

        } finally {

            closeSocket();
        }
    }

    private void acquireMulticastLock() {

        try {

            WifiManager wifiManager =
                    (WifiManager)
                            context.getSystemService(
                                    Context.WIFI_SERVICE
                            );

            if (wifiManager == null) {
                return;
            }

            multicastLock =
                    wifiManager.createMulticastLock(
                            "YG-Sync-Discovery"
                    );

            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();

            Log.d(
                    TAG,
                    "WiFi multicast lock acquired"
            );

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "Unable to acquire multicast lock: "
                            + error.getMessage()
            );
        }
    }

    public synchronized void stop() {

        if (!running) {
            return;
        }

        running = false;

        closeSocket();

        releaseMulticastLock();

        if (workerThread != null) {

            workerThread.interrupt();
            workerThread = null;
        }

        Log.d(
                TAG,
                "YG Sync discovery server stopped"
        );
    }

    private void closeSocket() {

        if (socket != null) {

            try {
                socket.close();
            } catch (Exception ignored) {
            }

            socket = null;
        }
    }

    private void releaseMulticastLock() {

        if (
                multicastLock != null
                && multicastLock.isHeld()
        ) {

            try {
                multicastLock.release();
            } catch (Exception ignored) {
            }
        }

        multicastLock = null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return DEFAULT_PORT;
    }
  }
