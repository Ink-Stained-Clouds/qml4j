package io.github.timer_err.qml4j.render;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Context2DWaveTest {

    @Test
    void sampledTrackMatchesQmlMoveAndLineLoopBitForBit() throws Exception {
        double x0 = 3;
        double x1 = 197;
        double step = 2;
        double cy = 8;
        double amplitude = 4;
        double frequency = 0.1;
        double phase = 1.23456789;

        Context2D actual = new Context2D(null, null);
        actual.appendSineWave(x0, x1, step, cy, amplitude, frequency, phase, false);

        List<Float> expected = new ArrayList<>();
        for (double x = x0; x <= x1; x += step) {
            expected.add(x == x0 ? 0f : 1f);
            expected.add((float) x);
            expected.add((float) (cy + amplitude * Math.sin((x * frequency) + phase)));
        }
        assertArrayEquals(toArray(expected), commands(actual));
    }

    @Test
    void determinateWaveMatchesQmlLoopAndExactTipBitForBit() throws Exception {
        double x0 = 3;
        double endX = 76.375;
        double step = 2;
        double cy = 8;
        double amplitude = 4;
        double frequency = 0.1;
        double phase = 5.25;

        Context2D actual = new Context2D(null, null);
        actual.appendSineWave(x0, endX, step, cy, amplitude, frequency, phase, true);

        List<Float> expected = new ArrayList<>();
        boolean started = false;
        for (double x = x0; x < endX; x += step) {
            expected.add(started ? 1f : 0f);
            expected.add((float) x);
            expected.add((float) (cy + amplitude * Math.sin((x * frequency) + phase)));
            started = true;
        }
        if (started) {
            expected.add(1f);
            expected.add((float) endX);
            expected.add((float) (cy + amplitude * Math.sin((endX * frequency) + phase)));
        }
        assertArrayEquals(toArray(expected), commands(actual));
    }

    @Test
    void indeterminateClipMatchesQmlFilteredSamplingGridBitForBit() throws Exception {
        double x0 = 3;
        double x1 = 197;
        double step = 2;
        double startX = -24.625;
        double endX = 73.375;
        double cy = 8;
        double amplitude = 4;
        double frequency = 0.1;
        double phase = 3.75;

        double firstX = x0 + Math.max(0, Math.ceil((startX - x0) / step)) * step;
        Context2D actual = new Context2D(null, null);
        actual.appendSineWave(firstX, Math.min(x1, endX), step,
            cy, amplitude, frequency, phase, false);

        List<Float> expected = new ArrayList<>();
        boolean begun = false;
        for (double x = x0; x <= x1; x += step) {
            if (x >= startX && x <= endX) {
                expected.add(begun ? 1f : 0f);
                expected.add((float) x);
                expected.add((float) (cy + amplitude * Math.sin((x * frequency) + phase)));
                begun = true;
            }
        }
        assertArrayEquals(toArray(expected), commands(actual));
    }

    private static float[] commands(Context2D context) throws Exception {
        Field cmdField = Context2D.class.getDeclaredField("cmd");
        Field lenField = Context2D.class.getDeclaredField("cmdLen");
        cmdField.setAccessible(true);
        lenField.setAccessible(true);
        float[] buffer = (float[]) cmdField.get(context);
        int length = (int) lenField.get(context);
        return java.util.Arrays.copyOf(buffer, length);
    }

    private static float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}
