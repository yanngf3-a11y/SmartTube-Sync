package com.liskovsoft.smartyoutubetv2.ygsync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YgSyncServer {

    public interface Listener {
        void onConnected(Socket socket);
        void onCommand(String command, Socket socket);
        void onDisconnected(Socket socket);
        void onError(Exception error);
    }

    public static final int DEFAULT_PORT = 8765;

    private final int port;
    private final Listener listener;

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private volatile boolean running = false;

    private ServerSocket serverSocket;

    public YgSyncServer(
            Listener listener
    ) {
        this(
                DEFAULT_PORT,
                listener
        );
    }

    public YgSyncServer(
            int port,
            Listener listener
    ) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized void start() {

        if (running) {
            return;
        }

        running = true;

        executor.execute(
                this::runServer
        );
    }

    private void runServer() {

        try {

            serverSocket =
                    new ServerSocket(port);

            serverSocket.setReuseAddress(true);

            while (running) {

                try {

                    Socket socket =
                            serverSocket.accept();

                    socket.setKeepAlive(true);

                    if (listener != null) {
                        listener.onConnected(socket);
                    }

                    executor.execute(
                            () -> handleClient(socket)
                    );

                } catch (IOException error) {

                    if (running &&
                            listener != null) {

                        listener.onError(error);
                    }
                }
            }

        } catch (Exception error) {

            if (listener != null) {
                listener.onError(error);
            }

        } finally {

            closeServerSocket();
        }
    }

    private void handleClient(
            Socket socket
    ) {

        try {

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()
                            )
                    );

            String command;

            while (
                    running &&
                    !socket.isClosed() &&
                    (command = reader.readLine()) != null
            ) {

                command = command.trim();

                if (command.isEmpty()) {
                    continue;
                }

                if (listener != null) {

                    try {

                        listener.onCommand(
                                command,
                                socket
                        );

                    } catch (Exception error) {

                        listener.onError(error);
                    }
                }
            }

        } catch (Exception error) {

            if (running &&
                    listener != null) {

                listener.onError(error);
            }

        } finally {

            if (listener != null) {

                try {
                    listener.onDisconnected(socket);
                } catch (Exception ignored) {
                }
            }

            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean send(
            Socket socket,
            String message
    ) {

        if (
                socket == null ||
                socket.isClosed() ||
                !socket.isConnected()
        ) {
            return false;
        }

        try {

            PrintWriter writer =
                    new PrintWriter(
                            socket.getOutputStream(),
                            true
                    );

            writer.println(message);

            return !writer.checkError();

        } catch (Exception error) {

            if (listener != null) {
                listener.onError(error);
            }

            return false;
        }
    }

    public synchronized void stop() {

        if (!running) {
            return;
        }

        running = false;

        closeServerSocket();

        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private void closeServerSocket() {

        if (serverSocket == null) {
            return;
        }

        try {
            serverSocket.close();
        } catch (Exception ignored) {
        }

        serverSocket = null;
    }
                          }
