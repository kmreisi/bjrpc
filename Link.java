package club.whuhu.jrpc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;

public class Link {

    public static class Service {
        public final String name;
        public final UUID uuid;

        public Service(String name, String uuid) {
            this.name = name;
            this.uuid = UUID.fromString(uuid);
        }
    }

    public static Service ANDROID_CAR_SERVICE_EVENT = new Service("Android Car Remote Event", "0000110f-0000-1000-8000-00805f9b12fb");
    public static Service ANDROID_CAR_SERVICE_ICON = new Service("Android Car Remote Icon", "0000111d-0000-1000-8000-00805f9b12fb");


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

    public void listen(Service service) throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothServerSocket serverSocket = null;
        socket = null;

        listener.connecting();

        try {

            // listen for incoming connections
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(service.name, service.uuid);

            // accept incomming connection
            socket = serverSocket.accept();

            // run JRPC on it, will send connected when ready
            jrpc.process(socket.getInputStream(), socket.getOutputStream(), listener);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Utils.closeSilently(serverSocket);
        Utils.closeSilently(socket);

        listener.disconnected();
    }

    public void connect(BluetoothDevice device, Service service) {
        if (device == null) {
            return;
        }

        socket = null;

        listener.connecting();

        try {
            // connect to device
            socket = device.createRfcommSocketToServiceRecord(service.uuid);
            socket.connect();

            // run JRPC on it, will send connected when ready
            jrpc.process(socket.getInputStream(), socket.getOutputStream(), listener);

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
