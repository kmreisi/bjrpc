package club.whuhu.jrpc;

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
                            link.listen(service);
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

    public void disconnect() {
        link.disconnect();
    }

    public JRPC getJrpc() {
        return link.getJrpc();
    }
}
