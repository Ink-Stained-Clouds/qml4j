package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public abstract class GroupAnimation extends Item implements Animatable {
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);

    private boolean wasRunning;

    public GroupAnimation() {
        visible.set(Boolean.FALSE);
    }

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
        if (c instanceof PropertyAnimation) return Boolean.TRUE.equals(((PropertyAnimation) c).running.peek());
        if (c instanceof GroupAnimation) return Boolean.TRUE.equals(((GroupAnimation) c).running.peek());
        return false;
    }

    protected static void startChild(Item c) {
        if (c instanceof PropertyAnimation) ((PropertyAnimation) c).running.set(Boolean.TRUE);
        else if (c instanceof GroupAnimation) ((GroupAnimation) c).running.set(Boolean.TRUE);
    }

    protected static void tickChild(Item c, long nowNanos) {
        if (c instanceof Animatable) ((Animatable) c).tick(nowNanos);
    }

    private void stopAllChildren() {
        for (Item c : children) {
            if (c instanceof PropertyAnimation) ((PropertyAnimation) c).running.set(Boolean.FALSE);
            else if (c instanceof GroupAnimation) ((GroupAnimation) c).running.set(Boolean.FALSE);
        }
    }
}
