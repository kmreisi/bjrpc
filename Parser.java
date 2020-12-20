package club.whuhu.jrpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Parser {

    private static enum ContainerType {
        CONTAINER_TYPE_UNDEFINED,
        CONTAINER_TYPE_OBJECT,
        CONTAINER_TYPE_ARRAY
    }

    private static enum ParseState {
        ParseStart,
        ParseArrayEntry,
        ParseObjectKeyStart,
        ParseObjectKey,
        ParseObjectSeperator,
        ParseNumber,
        ParseBoolean,
        ParseNull,
        ParseString
    }


    private static class Context {
        ParseState state;
        ContainerType type;
        InputStreamReader stream;
        boolean escaped;
        boolean complete;
        boolean skipSeperator;
        boolean forwardChar;
        char c = (char) -1;

        Context(InputStreamReader stream) {
            this.state = ParseState.ParseStart;
            this.type = ContainerType.CONTAINER_TYPE_UNDEFINED;
            this.stream = stream;
            this.complete = false;
            this.skipSeperator = false;
        }
    }

    public static boolean blank(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    public static boolean complete(Context ctx, char c, boolean onSeperator) throws InvalidObjectException {
        if (c == '}') {
            if (ctx.type != ContainerType.CONTAINER_TYPE_OBJECT) {
                throw new InvalidObjectException("Failed to parse entry, not an object but got: }");
            }
            ctx.complete = true;
            return true;
        }
        if (c == ']') {
            if (ctx.type != ContainerType.CONTAINER_TYPE_ARRAY) {
                throw new InvalidObjectException("Failed to parse entry, not an array but got: ]");
            }
            ctx.complete = true;
            return true;
        }
        if (onSeperator && c == ',') {
            if (!((ctx.type == ContainerType.CONTAINER_TYPE_ARRAY) || (ctx.type == ContainerType.CONTAINER_TYPE_OBJECT))) {
                throw new InvalidObjectException("Failed to parse entry, not an array or an object but got: ,");
            }
            ctx.skipSeperator = false;
            return true;
        }

        return false;
    }

    public static boolean skip(Context ctx, char c) throws InvalidObjectException {
        // skip whitespaces
        if (blank(c)) {
            return true;
        }

        // skip seperator {
        if (ctx.skipSeperator) {
            if (c != ',') {
                throw new InvalidObjectException("Failed to parse entry, expected seperator(,) but got: '" + c + "'.");
            }
            ctx.skipSeperator = false;
            return true;
        }

        return false;
    }

    public static char next(InputStreamReader stream) throws IOException {
        int c = stream.read();

        if (c == -1) {
            throw new IOException("Stream closed!");
        }

        return (char) c;
    }

    public static char unescape(char c) throws InvalidObjectException {
        switch (c) {
            case 'b': // Backspace
                return '\b';
            case 'f': // Form feed
                return '\f';
            case 'n': // Newline
                return '\n';
            case 'r': // Carriage
                return '\r';
            case 't': // Tab
                return '\t';
            case '"': // Double quote
                return '\"';
            case '\\': // Backslash
                return '\\';
            case '/': // XXX: how does this get here?!
                return '/';
            case '0':
                return '\0';
        }

        throw new InvalidObjectException("Failed to unescape character, \'" + c + "\' is not supported!");
    }

    public static Object parse(InputStreamReader stream) throws IOException {
        return parse(new Context(stream));
    }

    private static Object parse(Context parentContext) throws IOException {
        final Context ctx = new Context(parentContext.stream);
        ctx.forwardChar = parentContext.forwardChar;
        char c = (char) -1;

        StringBuilder read = new StringBuilder();
        Map<String, Object> object = null;
        List<Object> array = null;

        ctx.c = parentContext.c;

        // the input stream will throw an exception on close
        while (true) {
            if (!ctx.forwardChar) {
                ctx.c = next(ctx.stream);
            } else {
                ctx.forwardChar = false;
            }

            c = ctx.c;

            switch (ctx.state) {
                case ParseStart: {
                    // skip whitespaces
                    if (blank(c)) {
                        break;
                    }

                    // reset read buffer
                    read.setLength(0);

                    switch (c) {
                        case '{': {
                            // detected start of object
                            object = new HashMap<>();
                            ctx.state = ParseState.ParseObjectKeyStart;
                            ctx.type = ContainerType.CONTAINER_TYPE_OBJECT;
                            ctx.skipSeperator = false;
                            ctx.forwardChar = false;
                            break;
                        }
                        case '[': {
                            // detected start of array
                            array = new ArrayList<>();
                            ctx.state = ParseState.ParseArrayEntry;
                            ctx.type = ContainerType.CONTAINER_TYPE_ARRAY;
                            ctx.skipSeperator = false;
                            ctx.forwardChar = false;
                            break;
                        }
                        case '"': {
                            ctx.state = ParseState.ParseString;
                            read.setLength(0);
                            break;
                        }

                        case 'u':
                        case 'U':
                        case 'n':
                        case 'N': {
                            ctx.state = ParseState.ParseNull;
                            read.setLength(0);
                            read.append(c);
                            break;
                        }


                        case 't':
                        case 'T':
                        case 'f':
                        case 'F': {
                            ctx.state = ParseState.ParseBoolean;
                            read.setLength(0);
                            read.append(c);
                            break;
                        }

                        case '-':
                        case '.':
                        case '+':
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9': {
                            ctx.state = ParseState.ParseNumber;
                            read.setLength(0);
                            read.append(c);
                            break;
                        }
                        default: {
                            throw new InvalidObjectException(
                                    "Failed to parse entry, unexpected value " + c + " for " + read.toString());
                        }
                    }
                    break;
                }
                case ParseObjectKeyStart: {
                    // check if this is really an object (paranoid check)
                    if (object == null) {
                        throw new InvalidObjectException("Invalid parser state, reading an object without an object. Data: " + read.toString() + ".");
                    }

                    // check if complete
                    if (complete(ctx, c, false)) {
                        parentContext.complete = false;
                        parentContext.skipSeperator = true;
                        return object;
                    }

                    // skip whitespaces or seperator from action before
                    if (skip(ctx, c)) {
                        break;
                    }

                    // detect start of key
                    if (c == '"') {
                        ctx.state = ParseState.ParseObjectKey;
                        read.setLength(0);
                        break;
                    }

                    throw new InvalidObjectException("Failed to parse entry, expected start of key(\") got: " + c + ".");
                }
                case ParseObjectKey: {
                    // check if this is really an object (paranoid check)
                    if (object == null) {
                        throw new InvalidObjectException("Invalid parser state, reading an object without an object. Data: " + read.toString() + ".");
                    }

                    // on escaped mode append and reset escape mode
                    if (ctx.escaped) {
                        ctx.escaped = false;
                        read.append(unescape(c));
                        break;
                    }

                    // detect escape mode
                    if (c == '\\') {
                        ctx.escaped = true;
                        break;
                    }

                    // detect end of key, add
                    if (c == '"') {
                        ctx.state = ParseState.ParseObjectSeperator;
                        break;
                    }

                    // append current value
                    read.append(c);
                    break;
                }
                case ParseObjectSeperator: {
                    // check if this is really an object (paranoid check)
                    if (object == null) {
                        throw new InvalidObjectException("Invalid parser state, reading an object without an object. Data: " + read.toString() + ".");
                    }

                    // skip whitespaces
                    if (blank(c)) {
                        break;
                    }

                    // detect seperator, add object entry
                    if (c == ':') {
                        object.put(read.toString(), parse(ctx));
                        if (ctx.complete) {
                            return object;
                        } else {
                            ctx.state = ParseState.ParseObjectKeyStart;
                        }
                        break;
                    }

                    throw new InvalidObjectException("Failed to parse entry, expected seperator of key(:) got: " + c + ".");
                }

                case ParseArrayEntry: {
                    // check if this is really an array (paranoid check)
                    if (array == null) {
                        throw new InvalidObjectException("Invalid parser state, reading an array without an array. Data: " + read.toString() + ".");
                    }

                    // check if complete
                    if (complete(ctx, c, false)) {
                        parentContext.complete = false;
                        parentContext.skipSeperator = true;
                        return array;
                    }

                    // skip whitespaces or seperator from action before
                    if (skip(ctx, c)) {
                        break;
                    }

                    ctx.forwardChar = true;
                    array.add(parse(ctx));
                    ctx.forwardChar = false;
                    if (ctx.complete) {
                        return array;
                    }
                    break;
                }

                case ParseBoolean: {
                    if (complete(parentContext, c, true)) {
                        return Boolean.parseBoolean(read.toString());
                    }
                    read.append(c);
                    break;
                }
                case ParseNull: {
                    if (complete(parentContext, c, true)) {
                        String text = read.toString();
                        if (text.equalsIgnoreCase("undefined") || text.equalsIgnoreCase("null")) {
                            return null;
                        }
                    }
                    read.append(c);
                    break;
                }
                case ParseNumber: {
                    if (complete(parentContext, c, true)) {
                        String text = read.toString();
                        if (text.contains(".")) {
                            return Double.parseDouble(text);
                        } else {
                            return Long.decode(text);
                        }
                    }
                    read.append(c);
                    break;
                }
                case ParseString: {
                    // on escaped mode append and reset escape mode
                    if (ctx.escaped) {
                        ctx.escaped = false;
                        read.append(unescape(c));
                        break;
                    }

                    // detect escape mode
                    if (c == '\\') {
                        ctx.escaped = true;
                        break;
                    }

                    // detect end of value
                    if (c == '"') {
                        parentContext.complete = false;
                        parentContext.skipSeperator = true;
                        return read.toString();
                    }

                    // append
                    read.append(c);
                    break;
                }
            }
        }
    }

}
