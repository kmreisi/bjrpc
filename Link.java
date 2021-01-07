package club.whuhu.jrpc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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



    public static class Connection {
        public InputStream in;
        public OutputStream out;
        public Closeable closeable;
    }

    public interface ILinkStateListener {
        void connecting();

        void connected();

        void disconnected();
    }

    public interface IConnector {
        Connection connect() throws IOException;
    }

    private final JRPC jrpc;
    private final ILinkStateListener listener;

    private Connection connection = null;
    private Thread thread = null;
    private boolean running = false;

    public Link(ILinkStateListener listener) {
        this.jrpc = new JRPC();
        this.listener = listener;
    }

    public void start(final IConnector connector) {
        synchronized (this) {
            if (running) {
                return;
            }
            running = true;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        try {
                            listener.connecting();
                            connection = connector.connect();
                            jrpc.process(connection.in, connection.out, listener);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (connection != null) {
                                Utils.closeSilently(connection.closeable);
                                Utils.closeSilently(connection.in);
                                Utils.closeSilently(connection.out);
                            }
                            connection = null;
                            listener.disconnected();
                        }

                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            thread.start();
        }
    }

    public JRPC getJrpc() {
        return jrpc;
    }

    public void disconnect() {
        if (connection != null) {
            Utils.closeSilently(connection.closeable);
            Utils.closeSilently(connection.in);
            Utils.closeSilently(connection.out);
        }
    }

    public void stop() {
        synchronized (this) {
            running = false;
            disconnect();
            thread.notifyAll();
        }
    }

    public boolean isConnected() {
        return connection != null;
    }
}
