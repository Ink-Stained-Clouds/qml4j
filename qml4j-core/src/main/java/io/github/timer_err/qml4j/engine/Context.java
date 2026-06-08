package io.github.timer_err.qml4j.engine;

import java.util.HashMap;
import java.util.Map;

public final class Context {

    private final Context parent;
    private final Map<String, QObject> ids = new HashMap<>();

    public Context() {
        this(null);
    }

    public Context(Context parent) {
        this.parent = parent;
    }

    public Context parent() {
        return parent;
    }

    public void registerId(String id, QObject obj) {
        if (ids.containsKey(id)) {
            throw new IllegalStateException("duplicate id in context: " + id);
        }
        ids.put(id, obj);
    }

    public QObject lookupId(String id) {
        QObject local = ids.get(id);
        if (local != null) return local;
        return parent != null ? parent.lookupId(id) : null;
    }
}
