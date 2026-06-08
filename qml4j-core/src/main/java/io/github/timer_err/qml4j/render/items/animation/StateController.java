package io.github.timer_err.qml4j.render.items.animation;
import io.github.timer_err.qml4j.runtime.member.MemberAccess;
import io.github.timer_err.qml4j.render.items.core.Item;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StateController {

    private final Item owner;
    private State active;

    public StateController(Item owner) {
        this.owner = owner;
    }

    // Wire each state's `when` binding so the owner's active state tracks the first
    // state whose `when` is true. Only engages once some state actually declares a
    // when clause, so explicit `state:`-driven items are left untouched.
    public void initWhen() {
        for (State s : owner.states) {
            s.when.addChangeHandler(args -> evaluateWhen());
        }
        evaluateWhen();
    }

    private void evaluateWhen() {
        boolean declared = false;
        State match = null;
        for (State s : owner.states) {
            Object w = s.when.peek();
            if (w != null) declared = true;
            if (match == null && Boolean.TRUE.equals(w)) match = s;
        }
        if (!declared) return;
        owner.state.set(match != null ? match.name.peek() : null);
    }

    public void apply(String stateName) {
        State next = findState(stateName);
        if (next == active) return;
        Transition tr = findTransition(active, next);
        if (tr == null) {
            swap(next);
            return;
        }
        animatedSwap(next, tr);
    }

    private State findState(String name) {
        if (name == null || name.isEmpty()) return null;
        for (State s : owner.states) {
            if (name.equals(s.name.peek())) return s;
        }
        return null;
    }

    private Transition findTransition(State prev, State next) {
        String f = nameOf(prev);
        String t = nameOf(next);
        for (Transition tr : owner.transitions) {
            if (tr.matches(f, t)) return tr;
        }
        return null;
    }

    private static String nameOf(State s) {
        if (s == null) return "";
        String n = s.name.peek();
        return n != null ? n : "";
    }

    private void swap(State next) {
        if (active != null) active.revert();
        active = next;
        if (active != null) active.apply();
    }

    private void animatedSwap(State next, Transition tr) {
        List<TargetKey> keys = collectKeys(active, next);
        Map<TargetKey, Object> before = readAll(keys);
        swap(next);
        Map<TargetKey, Object> after = readAll(keys);
        List<PropertyAnimation> spawned = new ArrayList<>();
        for (Item child : tr.children) {
            if (child instanceof PropertyAnimation) {
                spawnAnimations((PropertyAnimation) child, before, after, spawned);
            }
        }
        driveRunning(tr, spawned);
    }

    // Qt's Transition.running is true while its animations play. Drive it here so
    // `onRunningChanged` fires -- the only completion hook QML has for a transition
    // (e.g. MD3 Menu closes its overlay in `onRunningChanged: if (!running) ...`).
    private void driveRunning(Transition tr, List<PropertyAnimation> spawned) {
        tr.running.set(Boolean.TRUE);
        if (spawned.isEmpty()) {
            tr.running.set(Boolean.FALSE);
            return;
        }
        int[] remaining = { spawned.size() };
        for (PropertyAnimation a : spawned) {
            a.finished.connect(() -> {
                if (--remaining[0] == 0) tr.running.set(Boolean.FALSE);
            });
        }
    }

    private static List<TargetKey> collectKeys(State a, State b) {
        List<TargetKey> keys = new ArrayList<>();
        addKeys(keys, a);
        addKeys(keys, b);
        return keys;
    }

    private static void addKeys(List<TargetKey> out, State s) {
        if (s == null) return;
        for (Item c : s.children) {
            if (!(c instanceof PropertyChanges)) continue;
            PropertyChanges pc = (PropertyChanges) c;
            Object t = pc.targetValue();
            if (t == null) continue;
            for (String name : pc.propertyNames()) {
                TargetKey k = new TargetKey(t, name);
                if (!out.contains(k)) out.add(k);
            }
        }
    }

    private static Map<TargetKey, Object> readAll(List<TargetKey> keys) {
        Map<TargetKey, Object> snap = new LinkedHashMap<>();
        for (TargetKey k : keys) {
            snap.put(k, MemberAccess.readMember(k.target, k.name));
        }
        return snap;
    }

    private void spawnAnimations(PropertyAnimation tpl,
                                 Map<TargetKey, Object> before,
                                 Map<TargetKey, Object> after,
                                 List<PropertyAnimation> spawned) {
        Object targetFilter = tpl.target.peek();
        String[] propFilter = parseCsv(tpl.properties.peek());
        for (Map.Entry<TargetKey, Object> e : after.entrySet()) {
            considerSpawn(tpl, e.getKey(), before.get(e.getKey()), e.getValue(),
                          targetFilter, propFilter, spawned);
        }
    }

    private void considerSpawn(PropertyAnimation tpl, TargetKey k,
                               Object beforeVal, Object afterVal,
                               Object targetFilter, String[] propFilter,
                               List<PropertyAnimation> spawned) {
        if (Objects.equals(beforeVal, afterVal)) return;
        if (targetFilter != null && targetFilter != k.target) return;
        if (propFilter != null && !contains(propFilter, k.name)) return;
        if (!tpl.acceptsTransition(beforeVal, afterVal)) return;
        cancelEphemeralsFor(k);
        PropertyAnimation a = buildEphemeral(tpl, k, beforeVal, afterVal);
        owner.children.add(a);
        spawned.add(a);
        MemberAccess.writeMember(k.target, k.name, beforeVal);
    }

    private void cancelEphemeralsFor(TargetKey k) {
        for (int i = owner.children.size() - 1; i >= 0; i--) {
            Item c = owner.children.get(i);
            if (!(c instanceof PropertyAnimation)) continue;
            PropertyAnimation a = (PropertyAnimation) c;
            if (!a.ephemeral) continue;
            if (a.target.peek() != k.target) continue;
            if (!k.name.equals(a.property.peek())) continue;
            a.running.set(Boolean.FALSE);
            owner.children.remove(i);
        }
    }

    private static PropertyAnimation buildEphemeral(PropertyAnimation tpl, TargetKey k,
                                                  Object from, Object to) {
        PropertyAnimation a = cloneTemplate(tpl);
        a.target.set(k.target);
        a.property.set(k.name);
        a.from.set(from);
        a.to.set(to);
        a.duration.set(tpl.duration.peek());
        a.easing.type.set(tpl.easing.type.peek());
        a.ephemeral = true;
        a.running.set(Boolean.TRUE);
        return a;
    }

    private static PropertyAnimation cloneTemplate(PropertyAnimation tpl) {
        try {
            PropertyAnimation a = tpl.getClass().getDeclaredConstructor().newInstance();
            if (a instanceof RotationAnimation && tpl instanceof RotationAnimation) {
                ((RotationAnimation) a).direction.set(((RotationAnimation) tpl).direction.peek());
            }
            return a;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot clone animation template " + tpl.getClass(), e);
        }
    }

    private static String[] parseCsv(String csv) {
        if (csv == null) return null;
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static boolean contains(String[] arr, String s) {
        for (String x : arr) {
            if (x.equals(s)) return true;
        }
        return false;
    }
}
