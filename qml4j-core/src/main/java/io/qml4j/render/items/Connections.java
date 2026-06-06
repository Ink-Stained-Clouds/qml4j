package io.qml4j.render.items;

import io.qml4j.engine.QObject;
import io.qml4j.engine.Signal;
import io.qml4j.engine.SignalHandler;
import io.qml4j.engine.SignalRelay;
import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public class Connections extends Item implements SignalRelay {
    public final Property<QObject> target = new Property<>(null);
    private final Map<String, SignalHandler> handlers = new LinkedHashMap<>();
    private QObject boundTarget;

    public Connections() {
        target.addListener(t -> rebind());
    }

    @Override
    public void connectSignal(String name, SignalHandler handler) {
        handlers.put(name, handler);
        QObject t = target.peek();
        if (t == boundTarget && t != null) {
            Signal s = findSignal(t, name);
            if (s != null) s.connect(handler);
        } else {
            rebind();
        }
    }

    private void rebind() {
        QObject next = target.peek();
        if (boundTarget != null) {
            for (Map.Entry<String, SignalHandler> e : handlers.entrySet()) {
                Signal s = findSignal(boundTarget, e.getKey());
                if (s != null) s.disconnect(e.getValue());
            }
        }
        boundTarget = next;
        if (next == null) return;
        for (Map.Entry<String, SignalHandler> e : handlers.entrySet()) {
            Signal s = findSignal(next, e.getKey());
            if (s != null) s.connect(e.getValue());
        }
    }

    private static Signal findSignal(QObject obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            Object v = f.get(obj);
            return v instanceof Signal ? (Signal) v : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }
}
