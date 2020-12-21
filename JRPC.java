package club.whuhu.jrpc;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JRPC {

    public static class Error extends Exception {
        private final Object id;
        private final long code;
        private final String msg;

        Error(Object id, long code, String msg) {
            super(msg);

            this.id = id;
            this.code = code;
            this.msg = msg;
        }

        public Error(Response r, long code, String msg) {
            this(r == null ? null : r.id, code, msg);
        }
    }

    public static class Response {
        private final Object id;
        private final Context ctx;

        Response(Object id, Context ctx) {
            this.id = id;
            this.ctx = ctx;
        }

        public void send(Object param) {
            // create package
            Map<String, Object> data = new HashMap<>();
            data.put("jsonrpc", "2.0");
            data.put("id", id);
            data.put("result", param);

            ctx.transmit( JsonUtils.mapToJson(data).toString());
        }
    }

    public static class Request {

        public interface CallbackResponse {
            void call(Object params);
        }

        public interface CallbackError {
            void call(Error error);
        }

        private final String method;
        private final Object params;
        private final CallbackResponse response;
        private final CallbackError error;
        private Object id = null;

        public Request(String method, Object params, CallbackResponse response, CallbackError error) {
            this.method = method;
            this.params = params;
            this.response = response;
            this.error = error;
        }
    }

    public static class  Notification extends Request {
        public Notification(String method, Object params) {
            super( method, params, null, null);
        }
    }

    public interface Method {
        void call(Response r, Object params) throws Error;
    }

    private final Map<String, Method> methods = new HashMap<>();

    public void register(String name, Method callback) {
        methods.put(name, callback);
    }

    private final AtomicReference<Context> ctx = new AtomicReference<>();

    public synchronized void process(final InputStream in, final OutputStream out) throws Exception {
        // set new context
        try {
            Context ctx = new Context(new InputStreamReader(in, "UTF-8"), out);
            this.ctx.set(ctx);
            // the parser will throw an exception on close
            ctx.run();
        } finally {
            // release context
            this.ctx.set(null);

            Utils.closeSilently(in);
            Utils.closeSilently(out);
        }
    }


    private class Context {
        private final AtomicLong currentId = new AtomicLong(Long.MIN_VALUE);
        private final Map<Object, Request> requests = new HashMap<>();
        private final InputStreamReader in;
        private final OutputStream out;
        private final BlockingQueue<byte[]> txQueue = new LinkedBlockingQueue<>();

        private Object getNextId() {
            synchronized (currentId) {
                if (currentId.compareAndSet(Long.MAX_VALUE, Long.MIN_VALUE)) {
                    return Long.MIN_VALUE;
                } else {
                    return currentId.incrementAndGet();
                }
            }
        }

        Context(InputStreamReader in, OutputStream out)  throws IOException, Error  {
            this.in = in;
            this.out = out;
        }

        private void run() throws Exception  {

            final AtomicBoolean running = new AtomicBoolean(true);
            final AtomicReference<Exception> error = new AtomicReference<>(null);

            Thread tx = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] toSend;
                        while (running.get() && (toSend = txQueue.take()) != null) {
                            if (toSend.length > 0) {
                                out.write(toSend);
                                out.flush();
                            }
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            error.compareAndSet(null, e);
                        }
                    } finally {
                        running.set(false);
                        Utils.closeSilently(in);
                        Utils.closeSilently(out);
                    }
                }

            });

            Thread rx = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (running.get()) {
                            //  will throw an exception on close
                            receive();
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            error.compareAndSet(null, e);
                        }
                    } finally {
                        running.set(false);
                        txQueue.add(new byte[0]);
                        Utils.closeSilently(in);
                        Utils.closeSilently(out);
                    }
                }
            });

            tx.start();
            rx.start();

            tx.join();
            rx.interrupt();
            rx.join();

            if (error.get() != null) {
                throw error.get();
            }
        }

        private void receive() throws Error {
            Map<String, Object> data;
            try {
                // try to parse the full object
                data = (Map<String, Object>)Parser.parse(in);
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
                throw new Error(null, -32700, e.getMessage());
            }

            // verify the package (JSON RPC 2)
            if (!"2.0".equals(data.get("jsonrpc"))) {
                throw new Error(null, -32600, "Not a JSON RPC 2.0 package");
            }

            Object id = data.get("id");

            // check if this is a result of an existing request
            Object result = data.get("result");
            if (result != null) {
                if (id == null) {
                    throw new Error(id, -32600, "Received result without ID.");
                }
                Request request;
                synchronized (requests) {
                    request = requests.remove(id);
                }

                if (request == null) {
                    throw new Error(id, -32603, "Received result to unknown request.");
                }

                Request.CallbackResponse response = request.response;
                if (response != null) {
                    response.call(result);
                }

                // done
                return;
            }

            Object method = data.get("method");
            if (!(method instanceof String)) {
                throw new Error(id, -32600, "No method specified.");
            }

            Method m = methods.get((String) method);
            if (m == null) {
                throw new Error(id, -32601, "Unknown Method.");
            }

            m.call(id == null ? null : new Response(id, this), data.get("params"));
        }

        public void send(Request request) {
            synchronized (requests) {
                if (!(request instanceof Notification)) {
                    request.id = getNextId();
                }
            }

            // create package
            Map<String, Object> data = new HashMap<>();
            data.put("jsonrpc", "2.0");
            data.put("id", request.id);
            data.put("method", request.method);
            data.put("params", request.params);

            synchronized (requests) {
                if (request.id != null) {
                    requests.put(request.id, request);
                }
            }
            transmit(JsonUtils.mapToJson(data).toString());
        }

        protected void transmit(String data) {
            try {
                txQueue.add(data.getBytes("UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void send(Request request) {
        Context ctx = this.ctx.get();
        if (ctx != null) {
            ctx.send(request);
        }
    }
}
