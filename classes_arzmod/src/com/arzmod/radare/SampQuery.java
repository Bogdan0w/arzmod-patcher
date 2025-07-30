package com.arzmod.radare;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SampQuery {

    public interface ServerNameCallback {
        void onResult(String name);
    }

    public static void getServerName(final String ip, final int port, final ServerNameCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = "SA:MP Server";
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket();
                    InetAddress address = InetAddress.getByName(ip);

                    byte[] request = new byte[11];
                    request[0] = 'S';
                    request[1] = 'A';
                    request[2] = 'M';
                    request[3] = 'P';

                    byte[] ipBytes = address.getAddress();
                    for (int i = 0; i < 4; i++) {
                        request[4 + i] = ipBytes[i];
                    }
                    request[8] = (byte) (port & 0xFF);
                    request[9] = (byte) ((port >> 8) & 0xFF);
                    request[10] = 'i';

                    DatagramPacket packet = new DatagramPacket(request, request.length, address, port);
                    socket.send(packet);

                    byte[] buffer = new byte[512];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.setSoTimeout(5000);
                    socket.receive(response);

                    int pos = 11; // header
                    pos += 1; // isPassword
                    pos += 2; // players
                    pos += 2; // maxplayers

                    int hostnameLen = (buffer[pos] & 0xFF) | ((buffer[pos + 1] & 0xFF) << 8)
                            | ((buffer[pos + 2] & 0xFF) << 16) | ((buffer[pos + 3] & 0xFF) << 24);
                    pos += 4;

                    if (hostnameLen > 0 && pos + hostnameLen <= response.getLength()) {
                        result = new String(buffer, pos, hostnameLen, "UTF-8");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                }
                callback.onResult(result);
            }
        }).start();
    }
}


