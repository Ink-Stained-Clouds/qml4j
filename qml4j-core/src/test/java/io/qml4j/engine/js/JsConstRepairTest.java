package io.qml4j.engine.js;

import io.qml4j.compiler.bytecode.rhino.RhinoFreeVars;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsConstRepairTest {

    // Two sibling for-blocks each declaring `const v` is legal ES6 but Rhino 1.9.1
    // rejects it; repair rewrites the keyword to `let`.
    @Test
    void rewritesSiblingBlockConsts() {
        String src = "for (let i=0;i<n;i++){const v=i;} for (let i=0;i<n;i++){const v=i;}";
        assertEquals("for (let i=0;i<n;i++){let v=i;} for (let i=0;i<n;i++){let v=i;}",
                JsConstRepair.repair(src));
    }

    @Test
    void leavesStringsAndCommentsUntouched() {
        String repaired = JsConstRepair.repair("const x = 'const y'; /* const z */");
        assertEquals("let x = 'const y'; /* const z */", repaired);
    }

    @Test
    void returnsNullWhenNoConst() {
        assertNull(JsConstRepair.repair("let a = b + c"));
        assertNull(JsConstRepair.repair("a + b"));
    }

    // The compile-time free-var scan rejected the chart bodies before the fix; now it
    // parses the repaired source instead of throwing.
    @Test
    void freeVarScanSurvivesConstRedecl() {
        String src = "for (let i=0;i<n;i++){const v=i; use(v);} "
                   + "for (let i=0;i<n;i++){const v=i; use(v);}";
        assertDoesNotThrow(() -> RhinoFreeVars.collect(src, Collections.emptySet()));
        assertTrue(RhinoFreeVars.collect(src, Collections.emptySet()).contains("n"));
    }
}
