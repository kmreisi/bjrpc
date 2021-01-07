package club.whuhu.jrpc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import club.whuhu.jrpc.Link;
import club.whuhu.jrpc.blue.BlueLink;

public class TcpLink extends Link {

    int assignedPort;

    public int getPort() {
        return assignedPort;
    }

    public TcpLink(ILinkStateListener listener) {
        super(listener);
    }

    public void listen(final int port) {
        start(new Link.IConnector() {
            @Override
            public Link.Connection connect() throws IOException {
                // listen for incoming connections
                ServerSocket server = new ServerSocket(port);
                assignedPort = server.getLocalPort();

                Socket socket = server.accept();
                server.close();

                Link.Connection connection = new Link.Connection();
                connection.in = socket.getInputStream();
                connection.out = socket.getOutputStream();
                connection.closeable = socket;

                return connection;
            }
        });
    }

    public void connect(final String host, final int port) {
        start(new Link.IConnector() {
            @Override
            public Link.Connection connect() throws IOException {
                Socket socket = new Socket(host, port);

                Link.Connection connection = new Link.Connection();
                connection.in = socket.getInputStream();
                connection.out = socket.getOutputStream();
                connection.closeable = socket;

                return connection;
            }
        });
    }
}
