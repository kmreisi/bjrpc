package club.whuhu.jrpc;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JRPC {

    private static final AtomicLong CURRENT_ID = new AtomicLong(Long.MIN_VALUE);
    private static final Map<Object, Request> REQUESTS = new HashMap<>();

    private static Object getNextId() {
        synchronized (CURRENT_ID) {
            if (CURRENT_ID.compareAndSet(Long.MAX_VALUE, Long.MIN_VALUE)) {
                return Long.MIN_VALUE;
            } else {
                return CURRENT_ID.incrementAndGet();
            }
        }
    }

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

    public class Context {
        InputStreamReader in;
        OutputStream out;
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

            transmit(ctx, JsonUtils.mapToJson(data).toString());
        }
    }

    public static class Request {
        public interface CallbackResponse {
            void call(Object params);
        }

        public interface CallbackError {
            void call(Error error);
        }

        private final Object id;
        private final String method;
        private final Object params;
        private final CallbackResponse response;
        private final CallbackError error;

        private Request(Object id, String method, Object params, CallbackResponse response, CallbackError error) {
            this.id = id;
            this.method = method;
            this.params = params;
            this.response = response;
            this.error = error;
        }

        public Request(String method, Object params, CallbackResponse response, CallbackError error) {
            this(getNextId(), method, params, response, error);
        }
    }

    public static class Notification extends Request {
        public Notification(String method, Object params) {
            super(null, method, params, null, null);
        }
    }

    public interface Method {
        void call(Response r, Object params) throws Error;
    }

    private final Map<String, Method> methods = new HashMap<>();

    public void register(String name, Method callback) {
        methods.put(name, callback);
    }


    private void error(InputStream stream) {

    }

    private final AtomicReference<Context> ctx = new AtomicReference<>();


    public synchronized void process(InputStream in, OutputStream out) throws IOException, Error {
        // set new context
        Context ctx = new Context();
        try {
            ctx.in = new InputStreamReader(in, "UTF-8");
            ctx.out = out;
            this.ctx.set(ctx);

            char c;
            // the parser will throw an exception on close
            while (true) {
                // try to parse object by object
                receive(ctx);
            }
        } finally {
            // release context
            this.ctx.set(null);

            Utils.closeSilently(ctx.in);
            Utils.closeSilently(ctx.out);
        }
    }

    private void receive(Context ctx) throws Error {
        Map<String, Object> data;
        try {
            // try to parse the full object
            data = (Map<String, Object>)Parser.parse(ctx.in);
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
            synchronized (REQUESTS) {
                request = REQUESTS.remove(id);
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

        m.call(id == null ? null : new Response(id, ctx), data.get("params"));
    }

    public void send(Request request) {
        // create package
        Map<String, Object> data = new HashMap<>();
        data.put("jsonrpc", "2.0");
        data.put("id", request.id);
        data.put("method", request.method);
        data.put("params", request.params);

        synchronized (REQUESTS) {
            if (request.id != null) {
                REQUESTS.put(request.id, request);
            }
        }
        transmit(ctx.get(), JsonUtils.mapToJson(data).toString());
    }

    protected static synchronized void transmit(Context ctx, String data) {
        if (ctx == null) {
            // not connected, TODO: throw exception
            return;
        }
        try {
            ctx.out.write(data.getBytes("UTF-8"));
            ctx.out.flush();
        } catch (IOException e) {
            // TODO; disconnect and forward exception
            e.printStackTrace();
        }
    }
}
