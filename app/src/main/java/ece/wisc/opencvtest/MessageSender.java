package ece.wisc.opencvtest;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executor;

public class MessageSender {
    protected Socket sock;
    protected DataOutputStream dos;
    protected PrintWriter pw;

    protected String message;

    private Executor executor;

    private String connection_address;

    public MessageSender(String address) {
        connection_address = address;
        new Thread(OpenSocketConnection).start();
    }

    public void destroy() {
        new Thread(CloseSocketConnection).start();
    }

    public void sendMouse(float x, float y) {
        message = "" + (int)x + ',' + (int)y;

        new Thread(SendSocketMessage).start();
    }

    private Runnable OpenSocketConnection = new Runnable() {
        @Override
        public void run() {
            try {
                sock = new Socket(connection_address, 7800);
                pw = new PrintWriter(sock.getOutputStream());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable CloseSocketConnection = new Runnable() {
        @Override
        public void run() {
            try {
                sock.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable SendSocketMessage = new Runnable() {
        @Override
        public void run() {
            pw.write(message);
            pw.flush();
        }
    };

    private void postMessage(String message) {
    }
}
