package io.github.jaffe2718.petprofile.util;

import io.github.jaffe2718.petprofile.data.LanTransferPayload;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class LanTransferClient {
    private static final long MAX_TRANSFER_BYTES = 512L * 1024L * 1024L;

    private LanTransferClient() {
    }

    public static byte[] download(LanTransferPayload payload) throws Exception {
        if (payload == null || payload.ip == null || payload.token == null || payload.port <= 0) {
            throw new IllegalArgumentException("Invalid LAN transfer payload.");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(payload.ip, payload.port), 15000);
            socket.setSoTimeout(300000);

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            byte[] tokenBytes = payload.token.getBytes(StandardCharsets.UTF_8);
            output.writeInt(tokenBytes.length);
            output.write(tokenBytes);
            output.flush();

            DataInputStream input = new DataInputStream(socket.getInputStream());
            long size = input.readLong();
            if (size < 0) {
                throw new IOException("LAN transfer token was rejected.");
            }
            if (size > MAX_TRANSFER_BYTES) {
                throw new IOException("LAN transfer payload is too large.");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) size);
            byte[] chunk = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = input.read(chunk, 0, (int) Math.min(chunk.length, remaining));
                if (read < 0) {
                    throw new IOException("Connection closed before transfer completed.");
                }
                buffer.write(chunk, 0, read);
                remaining -= read;
            }
            return buffer.toByteArray();
        }
    }
}
