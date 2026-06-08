package io.github.timer_err.qml4j.render.items.core;

import java.util.ArrayList;
import java.util.List;

public final class TextWrap {

    private TextWrap() {}

    public interface Measure {
        float widthOf(String s);
    }

    public static final class Result {
        public final List<String> lines;
        public final int[] starts;
        public Result(List<String> lines, int[] starts) {
            this.lines = lines;
            this.starts = starts;
        }
    }

    public static Result wrap(String text, String mode, float maxWidth, Measure m) {
        List<String> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        if (text == null) text = "";
        boolean wrapAny = "WrapAnywhere".equals(mode);
        // WordWrap breaks at word boundaries only; a word wider than the box overflows
        // rather than being split (Qt). Wrap falls back to a mid-word break when a single
        // word does not fit.
        boolean wordOnly = "WordWrap".equals(mode);
        boolean wrap = "Wrap".equals(mode) || wordOnly || wrapAny;
        int start = 0;
        int n = text.length();
        while (start <= n) {
            int hardBreak = text.indexOf('\n', start);
            int segEnd = hardBreak < 0 ? n : hardBreak;
            String seg = text.substring(start, segEnd);
            if (!wrap || maxWidth <= 0 || m.widthOf(seg) <= maxWidth) {
                starts.add(start);
                lines.add(seg);
            } else {
                wrapSegment(seg, start, maxWidth, m, wrapAny, wordOnly, lines, starts);
            }
            if (hardBreak < 0) break;
            start = hardBreak + 1;
            if (start > n) break;
            if (start == n) {
                starts.add(n);
                lines.add("");
                break;
            }
        }
        if (lines.isEmpty()) {
            starts.add(0);
            lines.add("");
        }
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = starts.get(i);
        return new Result(lines, arr);
    }

    private static void wrapSegment(String seg, int origin, float maxWidth, Measure m,
                                    boolean wrapAny, boolean wordOnly, List<String> outLines,
                                    List<Integer> outStarts) {
        int i = 0;
        int n = seg.length();
        while (i < n) {
            int hi = findBreakIndex(seg, i, n, maxWidth, m, wrapAny, wordOnly);
            if (hi == i) hi = i + 1;
            outStarts.add(origin + i);
            outLines.add(seg.substring(i, hi));
            i = hi;
        }
        if (n == 0) {
            outStarts.add(origin);
            outLines.add("");
        }
    }

    private static int findBreakIndex(String seg, int from, int end, float maxWidth,
                                      Measure m, boolean wrapAny, boolean wordOnly) {
        int lo = from + 1;
        int hi = end;
        int best = from + 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (m.widthOf(seg.substring(from, mid)) <= maxWidth) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (wrapAny || best == end) return best;
        int ws = seg.lastIndexOf(' ', best - 1);
        if (ws > from) return ws + 1;
        // No word boundary fits: the first word is wider than the box. WordWrap keeps it
        // whole on its own line (overflow); Wrap breaks it mid-word at `best`.
        if (wordOnly) {
            int sp = seg.indexOf(' ', best);
            return sp < 0 ? end : sp + 1;
        }
        return best;
    }

    public static int caretInLine(String line, float localX, Measure m) {
        if (line == null || line.isEmpty() || localX <= 0) return 0;
        float prev = 0f;
        int n = line.length();
        for (int i = 1; i <= n; i++) {
            float w = m.widthOf(line.substring(0, i));
            if (w >= localX) {
                float mid = (prev + w) / 2f;
                return localX < mid ? i - 1 : i;
            }
            prev = w;
        }
        return n;
    }

    public static int moveCaretVertical(Result r, int caret, int delta, Measure m) {
        int line = lineForCaret(r, caret);
        int target = line + delta;
        if (target < 0 || target >= r.lines.size()) return caret;
        int col = caret - r.starts[line];
        String src = r.lines.get(line);
        String dst = r.lines.get(target);
        float x = col <= 0 ? 0 : m.widthOf(src.substring(0, Math.min(col, src.length())));
        int newCol = caretInLine(dst, x, m);
        return r.starts[target] + newCol;
    }

    public static int lineForCaret(Result r, int caret) {
        int idx = 0;
        for (int i = 0; i < r.starts.length; i++) {
            if (r.starts[i] <= caret) idx = i;
            else break;
        }
        int end = idx + 1 < r.starts.length
            ? r.starts[idx + 1]
            : r.starts[idx] + r.lines.get(idx).length() + 1;
        if (caret >= end && idx + 1 < r.starts.length) idx++;
        return idx;
    }
}
