package io.github.timer_err.qml4j.render.items.animation;

final class TargetKey {
    final Object target;
    final String name;

    TargetKey(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TargetKey)) return false;
        TargetKey k = (TargetKey) o;
        return target == k.target && name.equals(k.name);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(target) * 31 + name.hashCode();
    }
}
