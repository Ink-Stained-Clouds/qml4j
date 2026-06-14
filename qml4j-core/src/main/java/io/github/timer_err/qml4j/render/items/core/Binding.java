package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.runtime.member.MemberAccess;

// QtQuick Binding element: `Binding { target: obj; property: "x"; value: expr; when: c }`.
// Writes `value` into `target[property]` whenever value/when/target settle and `when`
// is true. Our `value` is itself a bound Property, so this re-applies reactively as the
// source expression changes (a value-push approximation of Qt's installed binding).
public class Binding extends Item {

    public final Property<Object> target = new Property<>(null);
    public final Property<String> property = new Property<>(null);
    public final Property<Object> value = new Property<>(null);
    public final Property<Boolean> when = new Property<>(Boolean.TRUE);
    @SuppressWarnings("unused")
    public final Property<Boolean> delayed = new Property<>(Boolean.FALSE);

    public Binding() {
        visible.set(Boolean.FALSE);
        Runnable apply = this::apply;
        target.addInvalidationListener(apply);
        property.addInvalidationListener(apply);
        value.addInvalidationListener(apply);
        when.addInvalidationListener(apply);
    }

    private void apply() {
        Object t = target.peek();
        String p = property.peek();
        if (t == null || p == null || !Boolean.TRUE.equals(when.peek())) return;
        MemberAccess.writeMember(t, p, value.peek());
    }
}
