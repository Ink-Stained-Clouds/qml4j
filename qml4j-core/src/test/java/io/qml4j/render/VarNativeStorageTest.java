package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.mozilla.javascript.NativeArray;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A `var` property holds the live Rhino object, not a per-read deep copy. So
// arr.push(...) and in-place element mutation persist (QML var semantics), the
// object keeps its identity across reads, and Array methods still work -- while
// Java consumers (NativeArray is a List, NativeObject is a Map) keep seeing
// collections. The MD3 IndexBackground particle system relies on all of this:
// init() pushes particles, onPaint mutates p.x/p.y each frame.
//
// Functions are invoked from Java post-construction rather than Component.onCompleted,
// because a `var`'s initializer binding flushes after onCompleted (pre-existing
// ordering) and would read as null there -- unrelated to native storage.
class VarNativeStorageTest {

    private static Object read(Item o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void call(Item o, String fn) {
        try {
            for (Method m : o.getClass().getMethods()) {
                if (m.getName().equals(fn) && m.getParameterCount() == 0) {
                    m.invoke(o);
                    return;
                }
            }
            throw new NoSuchMethodException(fn);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void pushPersistsAndStaysNativeList() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property var arr: []\n"
            + "  property int n: -1\n"
            + "  function fill() { arr = []; arr.push({x: 1}); arr.push({x: 2}); n = arr.length }\n"
            + "}");
        call(root, "fill");
        assertEquals(2L, read(root, "n"));
        Object arr = read(root, "arr");
        assertTrue(arr instanceof NativeArray, "var array is the live NativeArray");
        assertEquals(2, ((NativeArray) arr).size(), "push persisted into stored array");
    }

    @Test
    void inPlaceElementMutationPersists() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property var arr: [{v: 10}, {v: 20}]\n"
            + "  property int sum: -1\n"
            + "  function bump() {\n"
            + "    for (var i = 0; i < arr.length; i++) arr[i].v += 5\n"
            + "    var s = 0; for (var j = 0; j < arr.length; j++) s += arr[j].v\n"
            + "    sum = s\n"
            + "  }\n"
            + "}");
        call(root, "bump");
        // 15 + 25 -- the += wrote back through the same object both times.
        assertEquals(40L, read(root, "sum"));
    }

    @Test
    void identityPreservedAcrossReads() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property var obj: ({a: 1})\n"
            + "  property bool nonNull: false\n"
            + "  property bool same: false\n"
            + "  function probe() { nonNull = (obj !== null); same = (obj === obj) }\n"
            + "}");
        call(root, "probe");
        assertEquals(Boolean.TRUE, read(root, "nonNull"), "obj is a real object, not null");
        assertEquals(Boolean.TRUE, read(root, "same"), "both reads yield the same object");
    }

    @Test
    void arrayMethodsWorkOnStoredVar() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property var nums: [1, 2, 3, 4]\n"
            + "  property int doubledSum: -1\n"
            + "  property bool isArr: false\n"
            + "  function probe() {\n"
            + "    isArr = Array.isArray(nums)\n"
            + "    doubledSum = nums.map(function(x){ return x * 2 }).reduce(function(a,b){ return a + b }, 0)\n"
            + "  }\n"
            + "}");
        call(root, "probe");
        assertEquals(Boolean.TRUE, read(root, "isArr"));
        assertEquals(20L, read(root, "doubledSum"));
    }

    @Test
    void varArrayDrivesRepeater() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Row { id: row\n"
            + "  property var items: [{w: 10}, {w: 20}, {w: 30}]\n"
            + "  Repeater { model: row.items\n"
            + "    Rectangle { height: 5; width: modelData.w }\n"
            + "  }\n"
            + "}");
        // children[0] is the Repeater node; delegates follow.
        assertEquals(10.0, root.children.get(1).width.peek().doubleValue(), 1e-6);
        assertEquals(30.0, root.children.get(3).width.peek().doubleValue(), 1e-6);
    }
}
