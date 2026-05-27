package io.qml4j.render.items;

import io.qml4j.engine.RuntimeHelpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class StateController {

    private final Item owner;
    private State active;

    StateController(Item owner) {
        this.owner = owner;
    }

    void apply(String stateName) {
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
        for (Item child : tr.children) {
            if (child instanceof NumberAnimation) {
                spawnAnimations((NumberAnimation) child, before, after);
            }
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
            snap.put(k, RuntimeHelpers.readMember(k.target, k.name));
        }
        return snap;
    }

    private void spawnAnimations(NumberAnimation tpl,
                                 Map<TargetKey, Object> before,
                                 Map<TargetKey, Object> after) {
        Object targetFilter = tpl.target.peek();
        String[] propFilter = parseCsv(tpl.properties.peek());
        for (Map.Entry<TargetKey, Object> e : after.entrySet()) {
            considerSpawn(tpl, e.getKey(), before.get(e.getKey()), e.getValue(),
                          targetFilter, propFilter);
        }
    }

    private void considerSpawn(NumberAnimation tpl, TargetKey k,
                               Object beforeVal, Object afterVal,
                               Object targetFilter, String[] propFilter) {
        if (Objects.equals(beforeVal, afterVal)) return;
        if (targetFilter != null && targetFilter != k.target) return;
        if (propFilter != null && !contains(propFilter, k.name)) return;
        if (!(beforeVal instanceof Number) || !(afterVal instanceof Number)) return;
        cancelEphemeralsFor(k);
        owner.children.add(buildEphemeral(tpl, k, (Number) beforeVal, (Number) afterVal));
        RuntimeHelpers.writeMember(k.target, k.name, beforeVal);
    }

    private void cancelEphemeralsFor(TargetKey k) {
        for (int i = owner.children.size() - 1; i >= 0; i--) {
            Item c = owner.children.get(i);
            if (!(c instanceof NumberAnimation)) continue;
            NumberAnimation a = (NumberAnimation) c;
            if (!a.ephemeral) continue;
            if (a.target.peek() != k.target) continue;
            if (!k.name.equals(a.property.peek())) continue;
            a.running.set(Boolean.FALSE);
            owner.children.remove(i);
        }
    }

    private static NumberAnimation buildEphemeral(NumberAnimation tpl, TargetKey k,
                                                  Number from, Number to) {
        NumberAnimation a = new NumberAnimation();
        a.target.set(k.target);
        a.property.set(k.name);
        a.from.set(from);
        a.to.set(to);
        a.duration.set(tpl.duration.peek());
        a.easing.set(tpl.easing.peek());
        a.ephemeral = true;
        a.running.set(Boolean.TRUE);
        return a;
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
