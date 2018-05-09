package chasm;

import java.io.Writer;
import java.io.PrintWriter;

public final class SExpPrinter {
    private final PrintWriter p;
    private int indent = 0;
    private enum Pos {
        BEGIN,
        LPAR,
        RPAR,
        VAL,
        END,
        INDENT,
    }
    private Pos pos = Pos.BEGIN;

    public SExpPrinter(final Writer w) {
        this.p = new PrintWriter(w);
    }

    public void begin() {
        space();
        p.print('(');
        pos = Pos.LPAR;
    }

    public void block(final String s) {
        begin();
        sym(s);
    }

    public void newLine() {
        pos = Pos.END;
    }

    public void end() {
        p.print(')');
        pos = Pos.RPAR;
    }

    public void endLine() {
        p.print(')');
        pos = Pos.END;
    }

    public void indent() {
        if (pos == Pos.LPAR)
            ++indent;
        else
            pos = Pos.INDENT;
    }

    public void unindent() {
        if (pos != Pos.INDENT)
            --indent;
    }

    public void val(final boolean v) {
        space();
        p.print(v);
    }

    public void val(final byte v) {
        space();
        p.print(v);
    }

    public void val(final short v) {
        space();
        p.print(v);
    }

    public void val(final char v) {
        space();
        p.print('\'');
        p.print(Escape.escapeChar(v));
        p.print('\'');
    }

    public void val(final long v) {
        space();
        p.print(v);
    }

    public void val(final int v) {
        space();
        p.print(v);
    }

    public void val(final float v) {
        space();
        p.print(v);
    }

    public void val(final double v) {
        space();
        p.print(v);
    }

    public void val(final String v) {
        space();
        if (v == null) {
            p.print("null");
        } else {
            p.print('"');
            p.print(Escape.escapeString(v));
            p.print('"');
        }
    }

    public void syms(final String[] v) {
        if (v == null) {
            sym("null");
        } else {
            begin();
            for (final String s : v)
                sym(s);
            end();
        }
    }

    public void sym(final String s) {
        space();
        p.print(s == null ? "null" : s);
    }

    @SuppressWarnings("fallthrough")
    private void space() {
        switch (pos) {
        case END:
            --indent;
            // fallthrough

        case INDENT:
            p.print('\n');
            ++indent;
            // fallthrough

        case BEGIN:
            for (int i = 0; i < indent; ++i)
                p.print(' ');
            pos = Pos.VAL;
            break;

        case LPAR:
            pos = Pos.VAL;
            break;

        case RPAR:
        case VAL:
            p.print(' ');
            break;
        }
    }

    public void flush() {
        p.flush();
    }
}
