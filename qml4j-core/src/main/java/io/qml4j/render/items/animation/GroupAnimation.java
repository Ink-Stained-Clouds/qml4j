package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import java.util.List;

public abstract class GroupAnimation extends AbstractAnimation {

    // Qt's SequentialAnimation/ParallelAnimation.animations: the child animations. They
    // are this group's children, so alias the list -- MD3 Tabs retargets a running move
    // via `moveAnim.animations[0].to = x`.
    public final List<Item> animations = children;

    private boolean wasRunning;
    private int loopsDone;

    @Override
    public final void tick(long nowNanos) {
        boolean r = Boolean.TRUE.equals(running.peek());
        if (r && !wasRunning) { onStart(nowNanos); loopsDone = 0; }
        wasRunning = r;
        if (!r) return;
        onTick(nowNanos);
        if (isFinished()) {
            int total = loops.peekInt(); // Animation.Infinite (-1) loops forever
            loopsDone++;
            stopAllChildren();
            if (total >= 0 && loopsDone >= total) {
                running.set(Boolean.FALSE);
                wasRunning = false;
                finished.emit();
            } else {
                onStart(nowNanos); // replay the group
            }
        }
    }

    @Override
    public void stop() {
        running.set(Boolean.FALSE);
        wasRunning = false;
        loopsDone = 0;
        stopAllChildren();
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
            if (c instanceof AbstractAnimation) ((AbstractAnimation) c).stop();
        }
    }
}
