package io.github.timer_err.qml4j.render.items.animation;
import io.github.timer_err.qml4j.render.items.core.Item;

public class ParallelAnimation extends GroupAnimation {

    @Override
    protected void onStart(long nowNanos) {
        for (Item c : children) startChild(c);
    }

    @Override
    protected void onTick(long nowNanos) {
        for (Item c : children) tickChild(c, nowNanos);
    }

    @Override
    protected boolean isFinished() {
        for (Item c : children) {
            if (isChildRunning(c)) return false;
        }
        return true;
    }
}
