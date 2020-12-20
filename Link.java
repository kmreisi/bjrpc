package club.whuhu.jrpc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;

public class Link {

    private static final String SERVICE_NAME = "Android Car Remote";
    public static final UUID SERVICE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface ILinkStateListener {
        void connecting();
        void connected();
        void disconnected();
    }

    private final JRPC jrpc;
    private final ILinkStateListener listener;
    private BluetoothSocket socket;

    public Link(ILinkStateListener listener) {
        this.jrpc = new JRPC();
        this.listener = listener;
    }

    public void listen() throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothServerSocket serverSocket = null;
        socket = null;

        listener.connecting();

        try {

            // listen for incoming connections
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);

            // accept connection
            socket = serverSocket.accept();

            listener.connected();

            // run JRPC on it
            jrpc.process(socket.getInputStream(), socket.getOutputStream());

        } catch (Exception e) {
        }

        Utils.closeSilently(serverSocket);
        Utils.closeSilently(socket);

        listener.disconnected();
    }

    public void connect(BluetoothDevice device) {
        if (device == null) {
            return;
        }

        socket = null;

        listener.connecting();

        try {
            // connect to device
            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
            socket.connect();

            listener.connected();

            // run JRPC on it
            jrpc.process(socket.getInputStream(), socket.getOutputStream());

        } catch (Exception e) {
        }

        Utils.closeSilently(socket);

        listener.disconnected();
    }


    public JRPC getJrpc() {
        return jrpc;
    }

    public void disconnect() {
        Utils.closeSilently(socket);
    }
}
