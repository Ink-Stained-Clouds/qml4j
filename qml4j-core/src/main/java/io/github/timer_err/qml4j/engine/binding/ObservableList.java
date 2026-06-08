package io.github.timer_err.qml4j.engine.binding;

import java.util.ArrayList;
import java.util.Collection;

// An ArrayList whose structural changes are observable by the binding system. A binding
// that reads the list (e.g. `container.children[0].implicitHeight`) calls trackRead() to
// depend on the version; any add/remove re-evaluates it. This is what lets a wrapper size
// to a child attached AFTER the wrapper's own bindings first ran (default-property content
// is parented at the use site, once the wrapper is already constructed).
public final class ObservableList<E> extends ArrayList<E> {

    // A monotonically bumped counter; reads register the dependency, mutations fire it.
    private final Property<Long> version = new Property<>(0L);

    // Register the current binding's dependency on this list's structure.
    public void trackRead() {
        version.get();
    }

    private void bump() {
        version.setBypassInterceptor(version.peek() + 1);
    }

    @Override public boolean add(E e) {
        boolean r = super.add(e);
        bump();
        return r;
    }

    @Override public void add(int index, E e) {
        super.add(index, e);
        bump();
    }

    @Override public E remove(int index) {
        E r = super.remove(index);
        bump();
        return r;
    }

    @Override public boolean remove(Object o) {
        boolean r = super.remove(o);
        if (r) bump();
        return r;
    }

    @Override public boolean addAll(Collection<? extends E> c) {
        boolean r = super.addAll(c);
        if (r) bump();
        return r;
    }

    @Override public boolean addAll(int index, Collection<? extends E> c) {
        boolean r = super.addAll(index, c);
        if (r) bump();
        return r;
    }

    @Override public boolean removeAll(Collection<?> c) {
        boolean r = super.removeAll(c);
        if (r) bump();
        return r;
    }

    @Override public void clear() {
        boolean had = !isEmpty();
        super.clear();
        if (had) bump();
    }

    @Override public E set(int index, E e) {
        E r = super.set(index, e);
        bump();
        return r;
    }
}
