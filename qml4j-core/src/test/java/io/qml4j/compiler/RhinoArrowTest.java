package io.qml4j.compiler;

import io.qml4j.compiler.bytecode.rhino.RhinoArrow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RhinoArrowTest {

    @Test
    void blockBodyArrow() {
        RhinoArrow.Result r = RhinoArrow.parse("(mouse) => { foo(mouse) }");
        assertEquals(Arrays.asList("mouse"), r.params);
        assertEquals("{ foo(mouse) }", r.bodySource);
    }

    @Test
    void expressionBodyArrowAndSingleParam() {
        RhinoArrow.Result r = RhinoArrow.parse("e => e.accepted = true");
        assertEquals(Arrays.asList("e"), r.params);
        assertEquals("e.accepted = true", r.bodySource);
    }

    @Test
    void noParamsArrow() {
        RhinoArrow.Result r = RhinoArrow.parse("() => doStuff()");
        assertEquals(Arrays.asList(), r.params);
        assertEquals("doStuff()", r.bodySource);
    }

    @Test
    void nonArrowIsNull() {
        assertNull(RhinoArrow.parse("foo()"));
        assertNull(RhinoArrow.parse("{ foo(); bar() }"));
        assertNull(RhinoArrow.parse("a + b"));
    }
}
