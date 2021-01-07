package club.whuhu.jrpc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

public class Client {
    private final Link link;
    private final IGetBluetoothDevice getDevice;
    private final Link.Service service;

    public interface IGetBluetoothDevice {
        public BluetoothDevice getDevice();
    }

    public Client(Link.Service service, Link.ILinkStateListener listener, IGetBluetoothDevice getDevice) {
        link = new Link(listener);
        this.service = service;
        this.getDevice = getDevice;
    }

    public void start() {
        link.start(new Link.IConnector() {
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
