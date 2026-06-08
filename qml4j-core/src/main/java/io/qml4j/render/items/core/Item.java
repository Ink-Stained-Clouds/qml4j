package io.qml4j.render.items.core;
import io.qml4j.render.items.input.Keys;
import io.qml4j.render.items.effect.Layer;
import io.qml4j.render.items.layout.LayoutAttached;
import io.qml4j.render.items.animation.State;
import io.qml4j.render.items.animation.StateController;
import io.qml4j.render.items.animation.Transition;
import io.qml4j.render.items.transform.Transform;

import io.qml4j.render.AnchorLine;
import io.qml4j.render.Anchors;
import io.qml4j.render.Painter;
import io.qml4j.render.TextLayout;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.binding.ObservableList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Number> implicitWidth = new Property<>(0);
    public final Property<Number> implicitHeight = new Property<>(0);
    public double lastImplicitWidth = Double.NaN;
    public double lastImplicitHeight = Double.NaN;
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Boolean> enabled = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Number> rotation = new Property<>(0);
    public final Property<Number> scale = new Property<>(1.0);
    // Item.TransformOrigin: which point scale/rotation pivot around (default Center).
    public final Property<Number> transformOrigin = new Property<>(4);
    public final Property<Number> z = new Property<>(0);
    public final Property<Boolean> clip = new Property<>(Boolean.FALSE);
    public final Property<Boolean> antialiasing = new Property<>(Boolean.FALSE);
    public final Property<Item> parent = new Property<>(null);
    // Declared List (the compiler emits children accesses with a java/util/List descriptor)
    // but an ObservableList so structural changes re-evaluate dependent bindings.
    public final List<Item> children = new ObservableList<>();
    // Non-visual children (Behavior, ...) -- in the QML tree for the construction-complete
    // arming walk, but never measured/laid-out/drawn, and not in `children` so a binding
    // like `container.children[0]` resolves to the first real visual child (Qt: a Behavior
    // is a property modifier, not a child).
    public final List<Item> resources = new ArrayList<>();
    public final Anchors anchors = new Anchors();
    public final ChildrenRect childrenRect = new ChildrenRect();
    // QtQuick.Layouts attached props (Layout.fillWidth, Layout.leftMargin, ...).
    public final LayoutAttached Layout = new LayoutAttached();

    public final Property<AnchorLine> left = new Property<>(new AnchorLine(this, AnchorLine.Edge.LEFT));
    public final Property<AnchorLine> right = new Property<>(new AnchorLine(this, AnchorLine.Edge.RIGHT));
    public final Property<AnchorLine> top = new Property<>(new AnchorLine(this, AnchorLine.Edge.TOP));
    public final Property<AnchorLine> bottom = new Property<>(new AnchorLine(this, AnchorLine.Edge.BOTTOM));
    public final Property<AnchorLine> horizontalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.HORIZONTAL_CENTER));
    public final Property<AnchorLine> verticalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.VERTICAL_CENTER));

    public final Property<String> state = new Property<>(null);
    public final List<State> states = new ArrayList<>();
    public final List<Transition> transitions = new ArrayList<>();
    public final List<Transform> transform = new ArrayList<>();

    public final Property<Boolean> focus = new Property<>(Boolean.FALSE);
    public final Property<Boolean> activeFocus = new Property<>(Boolean.FALSE);
    public final Property<Boolean> activeFocusOnTab = new Property<>(Boolean.FALSE);

    public final Layer layer = new Layer();

    private final StateController stateController = new StateController(this);
    private boolean stateBindingsInited;
    private Keys keys;
    private Consumer<Item> focusHook;

    // The previous value of `parent`, so a change can move this between children lists.
    private Item lastParent;

    public Item() {
        state.addListener(stateController::apply);
        // Reparenting on a `parent` change (Qt): `parent: overlay` moves an item into the
        // overlay's children so it renders there (z:9999), not at its declaration site.
        parent.addListener(this::onParentChanged);
    }

    private void onParentChanged(Item np) {
        Item old = lastParent;
        lastParent = np;
        if (old == np || old == null) return;
        // Move only a visual child: if the old parent's children held it (a resources-parked
        // Behavior, or an object assigned to a property, never was) move it to the new
        // parent's children. The construction-time first set has old == null, so the item is
        // simply recorded; a later `parent:` change then relocates it.
        if (old.children.remove(this) && np != null && !np.children.contains(this)) {
            np.children.add(this);
        }
    }

    public Keys keys() {
        if (keys == null) keys = new Keys();
        return keys;
    }

    public Keys keysOrNull() {
        return keys;
    }

    public void installFocusHook(Consumer<Item> hook) {
        focusHook = hook;
    }

    // Activate declarative State.when bindings once the tree is built. Idempotent:
    // dynamically-instantiated subtrees (Loader pages, Repeater delegates) are walked
    // when attached, and a node already inited during the top-level walk is skipped.
    public void initStateBindings() {
        if (stateBindingsInited) return;
        stateBindingsInited = true;
        if (!states.isEmpty()) stateController.initWhen();
    }

    // Activate state bindings for a freshly-attached subtree. Loader/Repeater create
    // their content during a render pass, after the top-level QmlView.load walk, so
    // their `when`-driven states would otherwise never engage.
    public void initStateBindingsTree() {
        initStateBindings();
        for (Item c : children) c.initStateBindingsTree();
        for (Item r : resources) r.initStateBindingsTree();
    }

    // A `visible:` binding may evaluate to undefined (peek() == null); treat that as the
    // default (visible) so layout/hit-test/paint don't NPE on the unboxed boolean.
    public boolean isVisible() {
        return !Boolean.FALSE.equals(visible.peek());
    }

    // Container layout hook, invoked by the Renderer layout pass. Default no-op;
    // layout containers (Row/Column/*Layout) override to position their children.
    public void layout() {}

    // Paint hook, invoked by the Renderer paint pass once geometry/clip/transform
    // are set up. Default no-op; drawable items override to render themselves via
    // Painter primitives (so item subclasses never import skija directly).
    public void paint(Painter p, float w, float h, float alpha) {}

    // Measure hook, invoked by the Renderer layout pre-pass. Default no-op; items
    // with intrinsic content size (Text, Button) override to publish implicit size
    // via TextLayout (so item subclasses never touch font metrics directly).
    public void measure(TextLayout t) {}

    // QML Item.mapFromItem(source, x, y): map a point from source's coordinate
    // system into this item's. Returns a point ({x, y}); positions only (no
    // rotation/scale), which is what hit-math like Slider's drag needs.
    public Map<String, Object> mapFromItem(Object source, double x, double y) {
        double[] from = source instanceof Item ? ((Item) source).scenePosition() : new double[]{0, 0};
        double[] self = scenePosition();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("x", from[0] + x - self[0]);
        p.put("y", from[1] + y - self[1]);
        return p;
    }

    private double[] scenePosition() {
        double sx = 0, sy = 0;
        for (Item it = this; it != null; it = it.parent.peek()) {
            sx += it.x.peekDouble();
            sy += it.y.peekDouble();
            // A Flickable renders its content translated by (-contentX, -contentY), so a
            // descendant's on-screen position is offset by the scroll of each ancestor
            // Flickable (else mapFromItem/mapToItem return content, not screen, coords --
            // a popup anchored to the scene then lands off-screen).
            Item p = it.parent.peek();
            if (p instanceof Flickable) {
                sx -= ((Flickable) p).contentX.peekDouble();
                sy -= ((Flickable) p).contentY.peekDouble();
            }
        }
        return new double[]{sx, sy};
    }

    public void forceActiveFocus() {
        Item r = this;
        while (r.parent.peek() != null) r = r.parent.peek();
        if (r.focusHook != null) r.focusHook.accept(this);
    }

    // QML Object.destroy(): detach from the scene. Safe to call from inside an
    // animation tick because the tick walk iterates children in reverse by index.
    public void destroy() {
        Item p = parent.peek();
        if (p != null) p.children.remove(this);
        parent.set(null);
    }
}
