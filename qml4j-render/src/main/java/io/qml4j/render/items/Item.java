package io.qml4j.render.items;

import io.qml4j.render.Anchors;

import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.QObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Item> parent = new Property<>(null);
    public final List<Item> children = new ArrayList<>();
    public final Anchors anchors = new Anchors();

    public final Property<String> state = new Property<>(null);
    public final List<State> states = new ArrayList<>();
    public final List<Transition> transitions = new ArrayList<>();
    private State activeState;

    public Item() {
        state.addListener(this::applyState);
    }

    private void applyState(String stateName) {
        State next = null;
        if (stateName != null && !stateName.isEmpty()) {
            for (State s : states) {
                if (stateName.equals(s.name.peek())) { next = s; break; }
            }
        }
        if (next == activeState) return;

        String prevName = activeState != null && activeState.name.peek() != null
            ? activeState.name.peek() : "";
        String nextName = next != null && next.name.peek() != null
            ? next.name.peek() : "";
        Transition tr = findTransition(prevName, nextName);

        if (tr == null) {
            if (activeState != null) activeState.revert();
            activeState = next;
            if (activeState != null) activeState.apply();
            return;
        }

        Map<TargetKey, Object> before = snapshotState(next);
        if (activeState != null) activeState.revert();
        activeState = next;
        if (activeState != null) activeState.apply();
        Map<TargetKey, Object> after = snapshotState(next);

        for (Item child : tr.children) {
            if (!(child instanceof NumberAnimation)) continue;
            NumberAnimation tpl = (NumberAnimation) child;
            spawnTransitionAnimations(tpl, before, after);
        }
    }

    private Transition findTransition(String fromName, String toName) {
        for (Transition t : transitions) {
            if (t.matches(fromName, toName)) return t;
        }
        return null;
    }

    private Map<TargetKey, Object> snapshotState(State s) {
        Map<TargetKey, Object> snap = new LinkedHashMap<>();
        if (s == null) return snap;
        for (Item c : s.children) {
            if (!(c instanceof PropertyChanges)) continue;
            PropertyChanges pc = (PropertyChanges) c;
            Object t = pc.targetValue();
            if (t == null) continue;
            for (String name : pc.propertyNames()) {
                snap.put(new TargetKey(t, name), RuntimeHelpers.readMember(t, name));
            }
        }
        return snap;
    }

    private void spawnTransitionAnimations(NumberAnimation tpl,
                                           Map<TargetKey, Object> before,
                                           Map<TargetKey, Object> after) {
        Object filterTarget = tpl.target.peek();
        String propsCsv = tpl.properties.peek();
        String[] propFilter = propsCsv != null ? splitCsv(propsCsv) : null;

        for (Map.Entry<TargetKey, Object> e : after.entrySet()) {
            TargetKey k = e.getKey();
            Object beforeVal = before.get(k);
            Object afterVal = e.getValue();
            if (Objects.equals(beforeVal, afterVal)) continue;
            if (filterTarget != null && filterTarget != k.target) continue;
            if (propFilter != null && !contains(propFilter, k.name)) continue;
            if (!(beforeVal instanceof Number) || !(afterVal instanceof Number)) continue;

            NumberAnimation anim = new NumberAnimation();
            anim.target.set(k.target);
            anim.property.set(k.name);
            anim.from.set((Number) beforeVal);
            anim.to.set((Number) afterVal);
            anim.duration.set(tpl.duration.peek());
            anim.easing.set(tpl.easing.peek());
            anim.ephemeral = true;
            anim.running.set(Boolean.TRUE);
            RuntimeHelpers.writeMember(k.target, k.name, beforeVal);
            children.add(anim);
        }
    }

    private static String[] splitCsv(String csv) {
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static boolean contains(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }

    private static final class TargetKey {
        final Object target;
        final String name;
        TargetKey(Object target, String name) { this.target = target; this.name = name; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof TargetKey)) return false;
            TargetKey k = (TargetKey) o;
            return target == k.target && name.equals(k.name);
        }
        @Override public int hashCode() {
            return System.identityHashCode(target) * 31 + name.hashCode();
        }
    }
}
