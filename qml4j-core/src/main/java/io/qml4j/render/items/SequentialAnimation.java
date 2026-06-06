package io.qml4j.render.items;

public class SequentialAnimation extends GroupAnimation {

    private int index;

    @Override
    protected void onStart(long nowNanos) {
        index = 0;
        if (!children.isEmpty()) startChild(children.get(0));
    }

    @Override
    protected void onTick(long nowNanos) {
        while (index < children.size()) {
            Item c = children.get(index);
            tickChild(c, nowNanos);
            if (isChildRunning(c)) return;
            index++;
            if (index < children.size()) {
                Item next = children.get(index);
                startChild(next);
                tickChild(next, nowNanos);
                if (isChildRunning(next)) return;
            }
        }
    }

    @Override
    protected boolean isFinished() {
        return index >= children.size();
    }
}
