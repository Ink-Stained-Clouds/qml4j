package io.qml4j.render;

import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.input.FocusScope;
import io.qml4j.render.items.input.TextEditable;

import java.util.ArrayList;
import java.util.List;

// Owns the active-focus item and tab-order navigation: focus transitions (with
// activeFocus/focus flag bookkeeping and focus-scope retargeting), Tab/Backtab
// cycling within the enclosing FocusScope, and the initial focus scan on load.
final class FocusManager {

    private Item root;
    private Item focused;
    private QmlView.FocusListener focusListener;

    void setRoot(Item root) {
        this.root = root;
    }

    Item focused() {
        return focused;
    }

    void setFocusListener(QmlView.FocusListener l) {
        this.focusListener = l;
    }

    void setFocus(Item it) {
        it = it == null ? null : focusTarget(it);
        if (focused == it) return;
        Item old = focused;
        if (old != null) {
            old.activeFocus.set(Boolean.FALSE);
            old.focus.set(Boolean.FALSE);
            if (old instanceof TextEditable) {
                TextEditable te = (TextEditable) old;
                te.setSelectionAnchor(-1);
                te.setSelectionRange(0, 0);
            }
        }
        focused = it;
        if (it != null) {
            it.focus.set(Boolean.TRUE);
            it.activeFocus.set(Boolean.TRUE);
        }
        if (focusListener != null) focusListener.onFocusChanged(it, old);
    }

    void clearFocus() {
        setFocus(null);
    }

    void scanInitialFocus(Item node) {
        if (node == null) return;
        if (Boolean.TRUE.equals(node.focus.peek())) {
            setFocus(node);
            return;
        }
        for (Item c : node.children) {
            scanInitialFocus(c);
            if (focused != null) return;
        }
    }

    boolean moveFocusByTab(boolean backward) {
        Item scope = enclosingScope(focused);
        List<Item> stops = new ArrayList<>();
        collectTabStops(scope, stops);
        if (stops.isEmpty()) return false;
        int idx = stops.indexOf(focused);
        int next;
        if (idx < 0) {
            next = backward ? stops.size() - 1 : 0;
        } else {
            next = backward ? (idx - 1 + stops.size()) % stops.size()
                            : (idx + 1) % stops.size();
        }
        setFocus(stops.get(next));
        return true;
    }

    private Item enclosingScope(Item it) {
        for (Item n = it; n != null; n = n.parent.peek()) {
            if (n instanceof FocusScope) return n;
        }
        return root;
    }

    private void collectTabStops(Item node, List<Item> out) {
        if (node == null || !Boolean.TRUE.equals(node.visible.peek())) return;
        if (Boolean.TRUE.equals(node.activeFocusOnTab.peek())) out.add(node);
        for (Item c : node.children) collectTabStops(c, out);
    }

    private Item focusTarget(Item it) {
        while (it instanceof FocusScope) {
            Item inner = scopeFocusChild(it);
            if (inner == null || inner == it) break;
            it = inner;
        }
        return it;
    }

    private Item scopeFocusChild(Item scope) {
        for (Item c : scope.children) {
            Item found = firstFocusInside(c);
            if (found != null) return found;
        }
        return null;
    }

    private Item firstFocusInside(Item node) {
        if (node == null || !Boolean.TRUE.equals(node.visible.peek())) return null;
        if (Boolean.TRUE.equals(node.focus.peek())) return node;
        for (Item c : node.children) {
            Item found = firstFocusInside(c);
            if (found != null) return found;
        }
        return null;
    }
}
