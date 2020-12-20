package club.whuhu.jrpc;

import android.bluetooth.BluetoothDevice;

public class Client {
    private final Link link;
    private final IGetBluetoothDevice device;
    private final Link.Service service;

    private Thread thread;
    private boolean running = false;

    public interface IGetBluetoothDevice {
        public BluetoothDevice getDevice();
    }

    public Client(Link.Service service, Link.ILinkStateListener listener, IGetBluetoothDevice device) {
        link = new Link(listener);
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

    public JRPC getJrpc() {
        return link.getJrpc();
    }
}
