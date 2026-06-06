package io.qml4j.render.items.view;
import io.qml4j.runtime.member.MemberAccess;
import io.qml4j.render.items.core.Item;

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

    // Qt Repeater.itemAt(index): the delegate instance at index, or null. MD3 Tabs uses
    // it to read the current tab's geometry when positioning the sliding indicator.
    public Item itemAt(int index) {
        return index >= 0 && index < instances.size() ? instances.get(index) : null;
    }

    public int count() {
        return instances.size();
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
        int desired = sizeOf(m);

        // Same count: update each delegate's index/modelData in place instead of
        // recreating it. Bindings depending on modelData recompute, but the delegate
        // (and any live state like an animating Ripple) survives. This is what makes a
        // SegmentedButton/NavigationBar keep its ripple when the selection model swaps.
        if (desired == instances.size() && desired > 0) {
            for (int i = 0; i < desired; i++) {
                Item d = instances.get(i);
                MemberAccess.writeMember(d, "index", (long) i);
                MemberAccess.writeMember(d, "modelData", dataAt(m, i));
            }
            return;
        }

        for (Item it : instances) visualParent.children.remove(it);
        instances.clear();
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
