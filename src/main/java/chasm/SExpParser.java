package chasm;

import java.io.Reader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.stream.Stream;

public final class SExpParser {
    private enum Token {
        LPAR,
        RPAR,
        STR,
        CHAR,
        SYM,
        INT,
        FLOAT,
        END,
    }

    public static final class SExpException extends RuntimeException {
        private static final long serialVersionUID = 0;

        public SExpException(final String msg, final Throwable cause) {
            super(msg, cause);
        }
    }

    private int lastChar = -1;
    private Token lastTok;
    private String strTok;
    private long intTok;
    private double floatTok;
    private final LineNumberReader reader;

    public SExpParser(final Reader r) {
        reader = new LineNumberReader(r);
    }

    public void begin() {
        expect(Token.LPAR);
    }

    public void end() {
        expect(Token.RPAR);
    }

    public void block(final String b) {
        begin();
        final String s = sym();
        if (!s.equals(b))
            err("Expected symbol \"" + b + "\", but got \"" + s + "\"");
    }

    public String sym() {
        expect(Token.SYM);
        return strTok.equals("null") ? null : strTok;
    }

    public boolean more() {
        lastTok = token();
        return lastTok != Token.RPAR && lastTok != Token.END;
    }

    public long longVal() {
        expect(Token.INT);
        return intTok;
    }

    public double doubleVal() {
        final Token got = token();
        if (got == Token.SYM) {
            if (strTok.equals("Infinity"))
                return 1.0/0.0;
            if (strTok.equals("NaN"))
                return 0.0/0.0;
        }
        lastTok = got;
        expect(Token.FLOAT);
        return floatTok;
    }

    public float floatVal() {
        return (float)doubleVal();
    }

    public byte byteVal() {
        return (byte)longVal();
    }

    public short shortVal() {
        return (short)longVal();
    }

    public int intVal() {
        return (int)longVal();
    }

    public String strVal() {
        if (isNull())
            return null;
        expect(Token.STR);
        return strTok;
    }

    public char charVal() {
        expect(Token.CHAR);
        return strTok.charAt(0);
    }

    public boolean boolVal() {
        expect(Token.SYM);
        if (!strTok.equals("true") && !strTok.equals("false"))
            err("Expected boolean");
        return strTok.equals("true");
    }

    public String[] syms() {
        if (isNull())
            return null;
        final Stream.Builder<String> list = Stream.builder();
        begin();
        while (more())
            list.add(sym());
        end();
        return list.build().toArray(String[]::new);
    }

    public boolean isNull() {
        final Token got = token();
        if (got == Token.SYM) {
            if (!strTok.equals("null"))
                err("Expected \"null\", but got \"" + strTok + "\"");
            return true;
        }
        lastTok = got;
        return false;
    }

    public boolean isStrVal() {
        final Token got = token();
        lastTok = got;
        return got == Token.STR || (got == Token.SYM && strTok.equals("null"));
    }

    public boolean isBoolVal() {
        final Token got = token();
        lastTok = got;
        return got == Token.SYM && (strTok.equals("true") || strTok.equals("false"));
    }

    private void expect(final Token tok) {
        final Token got = token();
        if (got != tok) {
            switch (got) {
            case SYM: case STR: err("Expected " + tok + ", but got " + got + ":" + strTok); break;
            case INT: err("Expected " + tok + ", but got " + got + ":" + intTok); break;
            default: err("Expected " + tok + ", but got " + got); break;
            }
        }
    }

    private Token token() {
        try {
            if (lastTok != null) {
                final Token t = lastTok;
                lastTok = null;
                return t;
            }

            int c;
            if (lastChar < 0) {
                c = reader.read();
            } else {
                c = lastChar;
                lastChar = -1;
            }

            for (;;) {
                if (c < 0)
                    return Token.END;
                if (c != ' ' && c != '\n')
                    break;
                c = reader.read();
            }

            if (c == '(')
                return Token.LPAR;

            if (c == ')')
                return Token.RPAR;

            if (c == '"' || c == '\'') {
                final int quote = c;
                final StringBuilder s = new StringBuilder();
                for (;;) {
                    c = reader.read();
                    if (c < 0)
                        err("Unexpected eof in string");
                    if (c == quote)
                        break;
                    if (c == '\\') {
                        s.append('\\');
                        c = reader.read();
                        if (c < 0)
                            err("Unexpected eof in string");
                    }
                    s.append((char)c);
                }
                strTok = Escape.unescapeString(s.toString());
                if (strTok == null)
                    err("Invalid string literal");
                if (quote == '"')
                    return Token.STR;
                if (strTok.length() > 1)
                    err("Character literal too long");
                return Token.CHAR;
            }

            if (c == '-' || (c >= '0' && c <= '9')) {
                boolean integer = true;
                final StringBuilder s = new StringBuilder();
                while ((c >= '0' && c <= '9') || c == 'e' || c == 'E' || c == '.' || c == '-' || c == 'f' || c == 'F') {
                    if (c == 'e' || c == 'E' || c == '.' || c == 'f' || c == 'F')
                        integer = false;
                    s.append((char)c);
                    c = reader.read();
                }

                if (c == 'I') {
                    final String inf = "Infinity";
                    int n = 0;
                    while (c == inf.charAt(n)) {
                        c = reader.read();
                        ++n;
                        if (n == inf.length()) {
                            lastChar = c;
                            floatTok = -1.0/0.0;
                            return Token.FLOAT;
                        }
                    }
                    err("Invalid numeric token");
                }

                lastChar = c;
                if (integer) {
                    intTok = new BigInteger(s.toString()).longValue();
                    return Token.INT;
                }
                floatTok = Double.parseDouble(s.toString());
                return Token.FLOAT;
            }

            final StringBuilder s = new StringBuilder();
            for (;;) {
                s.append((char)c);
                c = reader.read();
                if (c < 0 || c == ' ' || c == '\n' || c == '(' || c == ')' || c == '"')
                    break;
            }
            lastChar = c;
            strTok = s.toString();
            return Token.SYM;
        } catch (IOException e) {
            err("IOException", e);
            return null;
        }
    }

    public void err(final String msg) {
        err(msg, null);
    }

    public void err(final String msg, final Throwable cause) {
        throw new SExpException(msg + " at line " + (reader.getLineNumber() + 1), cause);
    }
}
