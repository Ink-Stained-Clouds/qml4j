package io.qml4j.render.items;

public abstract class GroupAnimation extends AbstractAnimation {

    private boolean wasRunning;

    @Override
    public final void tick(long nowNanos) {
        boolean r = Boolean.TRUE.equals(running.peek());
        if (r && !wasRunning) onStart(nowNanos);
        wasRunning = r;
        if (!r) return;
        onTick(nowNanos);
        if (isFinished()) {
            stopAllChildren();
            running.set(Boolean.FALSE);
            wasRunning = false;
        }
    }

    protected abstract void onStart(long nowNanos);
    protected abstract void onTick(long nowNanos);
    protected abstract boolean isFinished();

    protected static boolean isChildRunning(Item c) {
        return c instanceof AbstractAnimation
            && Boolean.TRUE.equals(((AbstractAnimation) c).running.peek());
    }

    protected static void startChild(Item c) {
        if (c instanceof AbstractAnimation) ((AbstractAnimation) c).running.set(Boolean.TRUE);
    }

    protected static void tickChild(Item c, long nowNanos) {
        if (c instanceof Animatable) ((Animatable) c).tick(nowNanos);
    }

    private void stopAllChildren() {
        for (Item c : children) {
            if (c instanceof AbstractAnimation) ((AbstractAnimation) c).running.set(Boolean.FALSE);
        }
    }
}
