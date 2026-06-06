package io.qml4j.render.items.view;
import io.qml4j.render.items.core.Flickable;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.QObject;
import io.qml4j.engine.SignalHandler;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

public class ListView extends Flickable implements DelegateHost {

    public final Property<Object> model = new Property<>(0);
    public final Property<Number> spacing = new Property<>(0);
    public final Property<String> orientation = new Property<>("Vertical");

    private DelegateFactory factory;
    private final List<Item> instances = new ArrayList<>();
    private ListModel boundModel;
    private SignalHandler modelListener;

    public ListView() {
        clip.set(Boolean.TRUE);
        model.addListener(v -> {
            attachModelSignals(v);
            rebuild();
        });
        spacing.addListener(v -> layout());
        orientation.addListener(v -> layout());
        width.addListener(v -> layout());
        height.addListener(v -> layout());
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
        for (Item it : instances) children.remove(it);
        instances.clear();
        Object m = model.peek();
        int n = sizeOf(m);
        for (int i = 0; i < n; i++) {
            Object data = dataAt(m, i);
            QObject created = factory.create(i, data, this);
            if (!(created instanceof Item)) {
                throw new IllegalStateException("ListView delegate must produce an Item");
            }
            Item item = (Item) created;
            children.add(item);
            instances.add(item);
        }
        layout();
    }

    private void layout() {
        float sp = spacing.peek().floatValue();
        boolean horizontal = "Horizontal".equals(orientation.peek());
        float cursor = 0f;
        for (Item it : instances) {
            if (horizontal) {
                it.x.set(cursor);
                it.y.set(0);
                cursor += it.width.peek().floatValue() + sp;
            } else {
                it.x.set(0);
                it.y.set(cursor);
                cursor += it.height.peek().floatValue() + sp;
            }
        }
        if (horizontal) {
            contentWidth.set(cursor > 0 ? cursor - sp : 0);
            contentHeight.set(height.peek());
        } else {
            contentHeight.set(cursor > 0 ? cursor - sp : 0);
            contentWidth.set(width.peek());
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
