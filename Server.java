package club.whuhu.jrpc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

public class Server {
    private final Link link;
    private final Link.Service service;

    private Thread thread;
    private boolean running = false;

    public Server(Link.Service service, Link.ILinkStateListener listener) {
        link = new Link(listener);
        this.service = service;
    }

    public void start() {
        link.start(new Link.IConnector() {
            @Override
            public Link.Connection connect() throws IOException {
                // listen for incoming connections
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothServerSocket serverSocket = adapter.listenUsingRfcommWithServiceRecord(service.name, service.uuid);

                // accept incomming connection
                BluetoothSocket

                        socket = serverSocket.accept();


                Link.Connection connection = new Link.Connection();
                connection.in = socket.getInputStream();
                connection.out = socket.getOutputStream();
                connection.closeable = socket;

                return connection;
            }
        });
    }

    public void stop() {
        link.stop();
    }

    public void disconnect() {
        link.disconnect();
    }

    public boolean isConnected() {
        return link.isConnected();
    }

    public JRPC getJrpc() {
        return link.getJrpc();
    }
}
