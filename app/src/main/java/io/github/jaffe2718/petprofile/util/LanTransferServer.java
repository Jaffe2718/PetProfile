package io.github.jaffe2718.petprofile.util;

import android.content.Context;

import io.github.jaffe2718.petprofile.data.ExportBundle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanTransferServer {
    public interface Callback {
        void onStarted(int port, String token);

        void onError(Throwable error);
    }

    private final Context context;
    private final ExportBundle bundle;
    private final String token = IdUtil.randomId();
    private volatile ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running;
    private volatile Socket activeSocket;

    public LanTransferServer(Context context, ExportBundle bundle) {
        this.context = context.getApplicationContext();
        this.bundle = bundle;
    }

    public String getToken() {
        return token;
    }

    public void start(Callback callback) {
        if (executor != null) {
            return;
        }
        running = true;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(0));
                if (!running) {
                    serverSocket.close();
                    return;
                }
                int port = serverSocket.getLocalPort();
                Async.ui(() -> callback.onStarted(port, token));
                acceptLoop();
            } catch (Throwable t) {
                running = false;
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        closeQuietly(activeSocket);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void acceptLoop() {
        while (running) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                activeSocket = socket;
                handle(socket);
            } catch (IOException e) {
                if (running) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            } catch (Throwable ignored) {
            } finally {
                if (activeSocket == socket) {
                    activeSocket = null;
                }
                closeQuietly(socket);
            }
        }
    }

    private void handle(Socket socket) throws Exception {
        socket.setSoTimeout(30000);
        try (DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            int tokenLength = input.readInt();
            if (tokenLength <= 0 || tokenLength > 1024) {
                output.writeLong(-1);
                output.flush();
                return;
            }
            byte[] tokenBytes = new byte[tokenLength];
            input.readFully(tokenBytes);
            String receivedToken = new String(tokenBytes, StandardCharsets.UTF_8);
            if (!token.equals(receivedToken)) {
                output.writeLong(-1);
                output.flush();
                return;
            }

            byte[] zipBytes = BackupManager.createZipBytes(context, bundle);
            output.writeLong(zipBytes.length);
            output.write(zipBytes);
            output.flush();
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
