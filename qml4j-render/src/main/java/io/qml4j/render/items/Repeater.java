package io.qml4j.render.items;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.QObject;
import io.qml4j.engine.SignalHandler;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

public class Repeater extends Item implements DelegateHost {
    public final Property<Object> model = new Property<>(0);
    private DelegateFactory factory;
    private final List<Item> instances = new ArrayList<>();
    private ListModel boundModel;
    private SignalHandler modelListener;

    public Repeater() {
        model.addListener(v -> {
            attachModelSignals(v);
            rebuild();
        });
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

    private void attachModelSignals(Object m) {
        if (boundModel != null && modelListener != null) {
            boundModel.rowsInserted.disconnect(modelListener);
            boundModel.rowsRemoved.disconnect(modelListener);
            boundModel.rowsChanged.disconnect(modelListener);
        }
        boundModel = null;
        modelListener = null;
        if (m instanceof ListModel) {
            boundModel = (ListModel) m;
            modelListener = args -> rebuild();
            boundModel.rowsInserted.connect(modelListener);
            boundModel.rowsRemoved.connect(modelListener);
            boundModel.rowsChanged.connect(modelListener);
        }
    }

    private void rebuild() {
        if (factory == null) return;
        Item visualParent = parent.peek();
        if (visualParent == null) return;
        Object m = model.peek();
        // ListModel rows can mutate per-index (set/swap), so tear down
        // and recreate from scratch. v0 cost is acceptable for small models.
        for (Item it : instances) visualParent.children.remove(it);
        instances.clear();
        int desired = sizeOf(m);
        for (int i = 0; i < desired; i++) {
            Object data = dataAt(m, i);
            QObject created = factory.create(i, data, visualParent);
            if (!(created instanceof Item)) {
                throw new IllegalStateException("Repeater delegate must produce an Item, got "
                    + (created == null ? "null" : created.getClass().getName()));
            }
            Item item = (Item) created;
            // parent was already set inside create() (before the delegate's bindings
            // were flushed, so outer-scope names resolve); just attach to the scene.
            visualParent.children.add(item);
            instances.add(item);
        }
    }

    private static int sizeOf(Object m) {
        if (m instanceof ListModel) return ((ListModel) m).rows.size();
        if (m instanceof Number) {
            int n = ((Number) m).intValue();
            return n < 0 ? 0 : n;
        }
        if (m instanceof List) return ((List<?>) m).size();
        return 0;
    }

    private static Object dataAt(Object m, int i) {
        if (m instanceof ListModel) {
            List<ListElement> rows = ((ListModel) m).rows;
            return i < rows.size() ? rows.get(i) : null;
        }
        if (m instanceof List) {
            List<?> list = (List<?>) m;
            return i < list.size() ? list.get(i) : null;
        }
        return i;
    }
}
