package io.qml4j.render.items;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.QObject;
import io.qml4j.engine.RuntimeHelpers;

import java.util.Map;

public class Component extends Item implements DelegateHost {
    private DelegateFactory factory;

    @Override
    public void setDelegate(DelegateFactory factory) {
        this.factory = factory;
    }

    public DelegateFactory factory() {
        return factory;
    }

    // QML Component.createObject(parent, properties): instantiate the delegate,
    // parent it into the scene, and apply the optional property map.
    public QObject createObject(Object parentObj, Object props) {
        if (factory == null) return null;
        QObject created = factory.create(0, null);
        if (created instanceof Item && parentObj instanceof Item) {
            Item child = (Item) created;
            child.parent.set((Item) parentObj);
            ((Item) parentObj).children.add(child);
        }
        if (props instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) props).entrySet()) {
                RuntimeHelpers.writeMember(created, String.valueOf(e.getKey()), e.getValue());
            }
        }
        return created;
    }

    public QObject createObject(Object parentObj) {
        return createObject(parentObj, null);
    }
}
