package io.qml4j.compiler;

import io.qml4j.compiler.bytecode.rhino.RhinoFreeVars;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhinoFreeVarsTest {

    private static Set<String> free(String src) {
        return new TreeSet<>(RhinoFreeVars.collect(src, Collections.emptySet()));
    }

    private static Set<String> of(String... names) {
        return new TreeSet<>(java.util.Arrays.asList(names));
    }

    @Test
    void binaryAndMemberAndCall() {
        assertEquals(of("a", "b"), free("a + b"));
        assertEquals(of("Math", "a", "b"), free("Math.max(a, b)"));
        assertEquals(of("obj"), free("obj.foo.bar"));
        assertEquals(of("arr", "i"), free("arr[i]"));
    }

    @Test
    void newAndTypeofAndTernary() {
        assertEquals(of("Date"), free("new Date().getHours()"));
        assertEquals(of("x"), free("typeof x === \"string\" ? x : x.text"));
        assertEquals(of(), free("typeof undefined"));
    }

    @Test
    void objectAndArrayLiteralsSkipKeys() {
        assertEquals(of("val", "bar"), free("({ key: val, nested: { deep: bar } })"));
        assertEquals(of("x", "rest"), free("[1, x, ...rest]"));
    }

    @Test
    void functionExpressionBindsParams() {
        assertEquals(of("items", "scale"),
            free("items.map(function(it){ return it.x + scale })"));
        assertEquals(of("items"), free("items.map(it => it.id)"));
        assertEquals(of("a", "b"), free("(x, y) => a + b + x + y - x - y"));
    }

    @Test
    void declarationsBindAndHoist() {
        assertEquals(of("a", "b"), free("{ var t = a; return t + b }"));
        // function declaration hoists: used before its definition, still bound.
        assertEquals(of("base"), free("{ helper(); function helper(){ return base } }"));
    }

    @Test
    void loopVariablesBind() {
        assertEquals(of("n", "sum", "arr"),
            free("{ for (var i = 0; i < n; i++) sum += arr[i] }"));
        assertEquals(of("list", "out"),
            free("{ for (var k in list) out.push(k) }"));
    }

    @Test
    void template() {
        assertEquals(of("a", "b"), free("`x ${a} y ${b.c} z`"));
    }
}
