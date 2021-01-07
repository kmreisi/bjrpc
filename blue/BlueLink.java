package club.whuhu.jrpc.blue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;

import club.whuhu.jrpc.Link;

public class BlueLink extends Link {

    public static class Service {
        public final String name;
        public final UUID uuid;

        public Service(String name, String uuid) {
            this.name = name;
            this.uuid = UUID.fromString(uuid);
        }
    }

    public static final Service ANDROID_CAR_SERVICE_EVENT = new Service("Android Car Remote Event", "0000110f-0000-1000-8000-00805f9b12fb");
    public static final Service ANDROID_CAR_SERVICE_ICON = new Service("Android Car Remote Icon", "0000111d-0000-1000-8000-00805f9b12fb");

    public interface IGetBluetoothDevice {
        BluetoothDevice getDevice();
    }

    public BlueLink(ILinkStateListener listener) {
        super(listener);
    }

    public void listen(final Service service) {
        start(new Link.IConnector() {
            @Override
            public Link.Connection connect() throws IOException {
                // listen for incoming connections
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothServerSocket serverSocket = adapter.listenUsingRfcommWithServiceRecord(service.name, service.uuid);

                // accept incoming connection
                BluetoothSocket socket = serverSocket.accept();
                serverSocket.close();

                Link.Connection connection = new Link.Connection();
                connection.in = socket.getInputStream();
                connection.out = socket.getOutputStream();
                connection.closeable = socket;

                return connection;
            }
        });
    }

    public void connect(final Service service, final IGetBluetoothDevice getDevice) {
        start(new Link.IConnector() {
            @Override
            public Link.Connection connect() throws IOException {
                BluetoothDevice device = getDevice.getDevice();

                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(service.uuid);
                socket.connect();

                Link.Connection connection = new Link.Connection();
                connection.in = socket.getInputStream();
                connection.out = socket.getOutputStream();
                connection.closeable = socket;

                return connection;
            }
        });
    }
}
