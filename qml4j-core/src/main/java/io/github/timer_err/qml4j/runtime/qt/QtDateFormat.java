package io.github.timer_err.qml4j.runtime.qt;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

// Qt.formatDate/formatTime/formatDateTime format-string support. Implements the Qt
// date/time field tokens (case-sensitive: M month, m minute, d day, h hour, y year,
// s second) over an epoch-millis value in the system zone. Unknown characters pass
// through literally. English locale matches the upstream MD3 widgets' day/month names.
public final class QtDateFormat {

    private QtDateFormat() {}

    public static String format(double millis, String fmt) {
        if (Double.isNaN(millis) || fmt == null) return "";
        ZonedDateTime t = Instant.ofEpochMilli((long) millis).atZone(ZoneId.systemDefault());
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = fmt.length();
        while (i < n) {
            char c = fmt.charAt(i);
            if ("yMdhHms".indexOf(c) < 0) {
                out.append(c);
                i++;
                continue;
            }
            int run = 1;
            while (i + run < n && fmt.charAt(i + run) == c) run++;
            out.append(field(t, c, run));
            i += run;
        }
        return out.toString();
    }

    private static String field(ZonedDateTime t, char c, int run) {
        switch (c) {
            case 'y': return run >= 4 ? pad(t.getYear(), 4) : pad(t.getYear() % 100, 2);
            case 'M':
                if (run >= 4) return t.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                if (run == 3) return t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                return run == 2 ? pad(t.getMonthValue(), 2) : Integer.toString(t.getMonthValue());
            case 'd':
                if (run >= 4) return t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                if (run == 3) return t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                return run == 2 ? pad(t.getDayOfMonth(), 2) : Integer.toString(t.getDayOfMonth());
            case 'h':
            case 'H': return run >= 2 ? pad(t.getHour(), 2) : Integer.toString(t.getHour());
            case 'm': return run >= 2 ? pad(t.getMinute(), 2) : Integer.toString(t.getMinute());
            case 's': return run >= 2 ? pad(t.getSecond(), 2) : Integer.toString(t.getSecond());
            default: return "";
        }
    }

    private static String pad(int v, int width) {
        String s = Integer.toString(v);
        StringBuilder b = new StringBuilder();
        for (int k = s.length(); k < width; k++) b.append('0');
        return b.append(s).toString();
    }
}
