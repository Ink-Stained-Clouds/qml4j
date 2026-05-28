package io.qml4j.render.items;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

public class Repeater extends Item implements DelegateHost {
    public final Property<Object> model = new Property<>(0);
    private DelegateFactory factory;
    private final List<Item> instances = new ArrayList<>();

    public Repeater() {
        model.addListener(v -> rebuild());
        parent.addListener(v -> rebuild());
    }

    @Override
    public void setDelegate(DelegateFactory factory) {
        this.factory = factory;
        rebuild();
    }

    public List<Item> instances() {
        return instances;
    }

    private void rebuild() {
        if (factory == null) return;
        Item visualParent = parent.peek();
        if (visualParent == null) return;
        Object m = model.peek();
        int desired = sizeOf(m);
        while (instances.size() > desired) {
            Item last = instances.remove(instances.size() - 1);
            visualParent.children.remove(last);
        }
        for (int i = instances.size(); i < desired; i++) {
            Object data = dataAt(m, i);
            QObject created = factory.create(i, data);
            if (!(created instanceof Item)) {
                throw new IllegalStateException("Repeater delegate must produce an Item, got "
                    + (created == null ? "null" : created.getClass().getName()));
            }
            Item item = (Item) created;
            item.parent.set(visualParent);
            visualParent.children.add(item);
            instances.add(item);
        }
    }

    private static int sizeOf(Object m) {
        if (m instanceof Number) {
            int n = ((Number) m).intValue();
            return n < 0 ? 0 : n;
        }
        if (m instanceof List) return ((List<?>) m).size();
        return 0;
    }

    private static Object dataAt(Object m, int i) {
        if (m instanceof List) {
            List<?> list = (List<?>) m;
            return i < list.size() ? list.get(i) : null;
        }
        return i;
    }
}
