package com.emcc.admin.app.util;

/** Small helper for building CSV text with correct quoting/escaping. */
public class CsvWriter {

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter line(Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(escape(values[i]));
        }
        sb.append("\r\n");
        return this;
    }

    public CsvWriter raw(String text) {
        sb.append(text).append("\r\n");
        return this;
    }

    public CsvWriter blank() {
        sb.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    private static String escape(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return (v.contains(",") || v.contains("\"") || v.contains("\n"))
                ? "\"" + v.replace("\"", "\"\"") + "\""
                : v;
    }
}
