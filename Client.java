package club.whuhu.jrpc;

import android.bluetooth.BluetoothDevice;

public class Client {
    private final Link link;
    private final IGetBluetoothDevice device;
    private final Link.Service service;

    private Thread thread;
    private boolean running = false;
    private boolean connected = false;

    public interface IGetBluetoothDevice {
        public BluetoothDevice getDevice();
    }

    public Client(Link.Service service, final Link.ILinkStateListener listener, IGetBluetoothDevice device) {
        link = new Link(new Link.ILinkStateListener() {
            @Override
            public void connecting() {
                listener.connecting();
            }

            @Override
            public void connected() {
                connected = true;
                listener.connected();
            }

            @Override
            public void disconnected() {
                connected = false;
                listener.disconnected();
            }
        });
        this.service = service;
        this.device = device;
    }

    public void start() {
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
                            link.connect(device.getDevice(), service);
                        } catch (Exception e) {
                            e.printStackTrace();
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

    public void stop() {
        synchronized (this) {
            running = false;
            link.disconnect();
            thread.notifyAll();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        link.disconnect();
    }

    public JRPC getJrpc() {
        return link.getJrpc();
    }
}
