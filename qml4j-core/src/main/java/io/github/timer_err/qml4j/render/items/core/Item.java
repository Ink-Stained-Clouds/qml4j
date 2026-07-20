package io.github.timer_err.qml4j.render.items.core;
import io.github.timer_err.qml4j.render.items.input.Keys;
import io.github.timer_err.qml4j.render.items.effect.Layer;
import io.github.timer_err.qml4j.render.items.layout.LayoutAttached;
import io.github.timer_err.qml4j.render.items.animation.State;
import io.github.timer_err.qml4j.render.items.animation.StateController;
import io.github.timer_err.qml4j.render.items.animation.Transition;
import io.github.timer_err.qml4j.render.items.transform.Transform;

import io.github.timer_err.qml4j.render.AnchorLine;
import io.github.timer_err.qml4j.render.Anchors;
import io.github.timer_err.qml4j.render.Painter;
import io.github.timer_err.qml4j.render.PolishQueue;
import io.github.timer_err.qml4j.render.TextLayout;

import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.binding.ObservableList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @SuppressWarnings("unused")
    public final Property<Boolean> antialiasing = new Property<>(Boolean.FALSE);
    // Opt-in static-subtree hint. When true, the layout pass skips re-measuring this
    // item's children as long as the item's own box and child count are unchanged --
    // for a container whose children's geometry is fixed once laid out (e.g. a full
    // song list), so an unrelated version bump (the 5 Hz play clock) doesn't
    // re-measure every off-screen row. See Renderer.measure.
    public final Property<Boolean> cachedLayout = new Property<>(Boolean.FALSE);
    public boolean cachedLayoutValid;
    public float cachedLayoutW;
    public float cachedLayoutH;
    public int cachedLayoutCount;
    public long cachedLayoutChildVersion = -1;
    public long cachedLayoutSettleId = -1;
    // Checksum of the direct children's sizes at cache time. A child whose width comes
    // from a binding (a list row at `width: view.width`) can settle a frame after the
    // first measure, while this container's own box is already final -- so the box +
    // count check above stays valid and the stale child never gets re-measured. Folding
    // the children's dimensions in catches that.
    public long cachedLayoutChildDims;
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

    @SuppressWarnings("unused")
    public final Property<AnchorLine> left = new Property<>(new AnchorLine(this, AnchorLine.Edge.LEFT));
    public final Property<AnchorLine> right = new Property<>(new AnchorLine(this, AnchorLine.Edge.RIGHT));
    @SuppressWarnings("unused")
    public final Property<AnchorLine> top = new Property<>(new AnchorLine(this, AnchorLine.Edge.TOP));
    @SuppressWarnings("unused")
    public final Property<AnchorLine> bottom = new Property<>(new AnchorLine(this, AnchorLine.Edge.BOTTOM));
    @SuppressWarnings("unused")
    public final Property<AnchorLine> horizontalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.HORIZONTAL_CENTER));
    @SuppressWarnings("unused")
    public final Property<AnchorLine> verticalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.VERTICAL_CENTER));

    public final Property<String> state = new Property<>(null);
    // Qt's objectName: an opt-in tag so a host can locate a specific item in the tree
    // (e.g. to render one subtree separately). Null unless QML sets it.
    public final Property<String> objectName = new Property<>(null);
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

    // --- Incremental layout invalidation (Qt-style per-Item dirty tracking) ---------------
    // Set when this item's position (x/y) or size (width/height/implicit*) has changed since
    // the last settle and it is enqueued in the PolishQueue for re-measure. The bits dedupe
    // re-marks within a settle and gate propagation so anchor cycles can't loop.
    public boolean posDirty;
    public boolean sizeDirty;
    // Items whose anchors resolve against THIS item's geometry -- populated by the renderer as
    // it applies anchors. When this item moves/resizes they must re-anchor, so they're marked
    // dirty too (the event-driven analogue of Qt's anchor signal connections). Null until an
    // anchor references this item, which is the common case (few items are anchor sources).
    private Set<Item> anchorDependents;

    public Item() {
        state.addListener(stateController::apply);
        // Reparenting on a `parent` change (Qt): `parent: overlay` moves an item into the
        // overlay's children so it renders there (z:9999), not at its declaration site.
        parent.addListener(this::onParentChanged);
        wireLayoutInvalidation();
    }

    // Whether this item computes its OWN size imperatively from its children in layout()
    // (Column/Row/*Layout/Flow/StackLayout). Only such a parent needs marking when a child's
    // size changes: that derived chain is not tracked by the binding system (it's plain Java),
    // so the incremental settle must propagate the child's size change up to it explicitly.
    // A size derived via a QML binding (implicitHeight: child.height) propagates through the
    // normal Property invalidation path and needs no override here.
    public boolean layoutDerivesSizeFromChildren() {
        return false;
    }

    // Connect the layout-affecting properties to the PolishQueue so a change marks this item
    // (and, for size, propagates to a size-deriving parent) instead of forcing a whole-tree
    // relayout. Fires only while a PolishQueue is installed (the incremental host path); with
    // none installed these are cheap no-ops and the renderer falls back to full measure.
    private void wireLayoutInvalidation() {
        x.addInvalidationListener(this::markLayoutPosition);
        y.addInvalidationListener(this::markLayoutPosition);
        width.addInvalidationListener(this::markLayoutSize);
        height.addInvalidationListener(this::markLayoutSize);
        implicitWidth.addInvalidationListener(this::markLayoutSize);
        implicitHeight.addInvalidationListener(this::markLayoutSize);
        visible.addInvalidationListener(this::markLayoutVisibility);
        // A structural change to the children (add/remove) can reflow a container even when no
        // surviving child fired a geometry change (a removal frees space in a Column).
        ((ObservableList<Item>) children).addStructuralListener(this::markLayoutSize);
        // Any anchor knob change (source, margins, centre offsets) can move/resize this item.
        anchors.fill.addInvalidationListener(this::markLayoutSize);
        anchors.centerIn.addInvalidationListener(this::markLayoutSize);
        anchors.margins.addInvalidationListener(this::markLayoutSize);
        anchors.leftMargin.addInvalidationListener(this::markLayoutSize);
        anchors.rightMargin.addInvalidationListener(this::markLayoutSize);
        anchors.topMargin.addInvalidationListener(this::markLayoutSize);
        anchors.bottomMargin.addInvalidationListener(this::markLayoutSize);
        anchors.left.addInvalidationListener(this::markLayoutSize);
        anchors.right.addInvalidationListener(this::markLayoutSize);
        anchors.top.addInvalidationListener(this::markLayoutSize);
        anchors.bottom.addInvalidationListener(this::markLayoutSize);
        anchors.horizontalCenter.addInvalidationListener(this::markLayoutSize);
        anchors.verticalCenter.addInvalidationListener(this::markLayoutSize);
        anchors.horizontalCenterOffset.addInvalidationListener(this::markLayoutSize);
        anchors.verticalCenterOffset.addInvalidationListener(this::markLayoutSize);
    }

    public void markLayoutPosition() {
        PolishQueue q = PolishQueue.current();
        if (q == null || posDirty) return; // already marked -> dependents already propagated
        posDirty = true;
        q.enqueue(this);
        // Items anchored to my position (anchors.left: sibling.right, ...) must re-anchor.
        propagateToAnchorDependents();
    }

    public void markLayoutSize() {
        PolishQueue q = PolishQueue.current();
        if (q == null || sizeDirty) return; // guard blocks anchor/derive cycles from looping
        sizeDirty = true;
        q.enqueue(this);
        Item p = parent.peek();
        if (p != null && p.layoutDerivesSizeFromChildren()) p.markLayoutSize();
        propagateToAnchorDependents();
    }

    private void markLayoutVisibility() {
        // Hiding: parent may reflow, so mark self (size). Showing: this subtree was skipped by
        // prior measures and has stale geometry -- mark the whole subtree so it re-measures.
        markLayoutSize();
        if (isVisible()) markSubtreeDirty();
    }

    private void markSubtreeDirty() {
        for (int i = 0, n = children.size(); i < n; i++) {
            Item c = children.get(i);
            c.markLayoutSize();
            c.markSubtreeDirty();
        }
    }

    private void propagateToAnchorDependents() {
        if (anchorDependents == null) return;
        for (Item d : anchorDependents) d.markLayoutSize();
    }

    // Register `dep` as anchoring against this item's geometry (called by the renderer while
    // resolving anchors). Idempotent; the set never removes stale entries, which is safe --
    // a stale dependent only causes a harmless extra re-measure, never a missed one.
    public void addAnchorDependent(Item dep) {
        if (dep == null || dep == this) return;
        if (anchorDependents == null) anchorDependents = new LinkedHashSet<>();
        anchorDependents.add(dep);
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

    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public void destroy() {
        dispose();
    }

    // Release native resources this item owns (decoded images, offscreen
    // surfaces). Default no-op; Image/Canvas override. Called per item when a
    // subtree is discarded.
    protected void releaseResources() {}

    // Discard this subtree. Unbinds every property's binding so external,
    // longer-lived properties (e.g. a row's `width: parent.width` or
    // `highlighted: index === player.index`) stop retaining these items through
    // their listener lists, releases native resources (cover images, canvas
    // backings), then detaches. Without this a Repeater/Loader that throws away
    // delegates leaks the whole delegate subtree AND its decoded images — opening
    // several big playlists then OOMs.
    public void dispose() {
        tearDown();
        Item p = parent.peek();
        if (p != null) p.children.remove(this);
        parent.set(null);
    }

    private void tearDown() {
        unbindAll();
        releaseResources();
        for (int i = 0; i < children.size(); i++) children.get(i).tearDown();
        for (int i = 0; i < resources.size(); i++) resources.get(i).tearDown();
    }

    private void unbindAll() {
        unbindFields(this, true);
    }

    // Unbind every Property reachable from `obj`: its own Property fields, and (one
    // level, when descendHolders) the Property fields of its nested holders (font,
    // anchors, Layout, ...). The holders matter because bindings like
    // `font.family: Theme.iconFont.name` live on a holder's Property, not a
    // top-level field — and Theme/StyleManager are singletons, so without clearing
    // them every discarded row stays pinned in their listener lists (the leak).
    private static void unbindFields(Object obj, boolean descendHolders) {
        for (Field f : fieldsOf(obj.getClass())) {
            Object v;
            try {
                v = f.get(obj);
            } catch (IllegalAccessException ignore) {
                continue; // public fields only; never thrown
            }
            if (v instanceof Property) {
                ((Property<?>) v).unbind();
            } else if (descendHolders && isEngineHolder(v)) {
                unbindFields(v, false);
            }
        }
    }

    // A non-Item, non-Property engine object that may hold bound Properties (Font,
    // Anchors, LayoutAttached, ChildrenRect, ...). Items are excluded — they're
    // torn down via the children/resources recursion, not as holders.
    private static boolean isEngineHolder(Object v) {
        if (v == null || v instanceof Item || v instanceof Property) return false;
        return v.getClass().getName().startsWith("io.github.timer_err.qml4j.");
    }

    // Cache the public fields per concrete class: dispose runs over every row of a
    // list on a model swap, and reflecting getFields() each time is a real cost at
    // hundreds of delegates.
    private static final Map<Class<?>, Field[]> FIELDS = new HashMap<>();

    private static Field[] fieldsOf(Class<?> cls) {
        Field[] cached = FIELDS.get(cls);
        if (cached == null) {
            cached = cls.getFields();
            FIELDS.put(cls, cached);
        }
        return cached;
    }
}
