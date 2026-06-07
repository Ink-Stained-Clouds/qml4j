package io.qml4j.engine.js;

import io.qml4j.compiler.bytecode.rhino.RhinoFreeVars;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsConstRepairTest {

    // Two sibling for-blocks each declaring `const v` is legal ES6 but Rhino 1.9.1
    // rejects it as a redeclaration; the rewrite turns the keyword into `let`.
    @Test
    void rewritesSiblingBlockConsts() {
        String src = "for (let i=0;i<n;i++){const v=i;} for (let i=0;i<n;i++){const v=i;}";
        assertEquals("for (let i=0;i<n;i++){let v=i;} for (let i=0;i<n;i++){let v=i;}",
                JsConstRepair.toLet(src));
    }

    // The silent bug: Rhino scopes `const` to the function, so a loop-body const keeps
    // its first value. Rewriting to `let` restores per-iteration binding.
    @Test
    void rewritesLoopBodyConst() {
        assertEquals("for (let i=0;i<n;i++){let v=data[i];}",
                JsConstRepair.toLet("for (let i=0;i<n;i++){const v=data[i];}"));
    }

    @Test
    void leavesStringsAndCommentsUntouched() {
        assertEquals("let x = 'const y'; /* const z */",
                JsConstRepair.toLet("const x = 'const y'; /* const z */"));
    }

    @Test
    void returnsInputWhenNoConstDeclaration() {
        assertEquals("let a = b + c", JsConstRepair.toLet("let a = b + c"));
        assertEquals("a + b", JsConstRepair.toLet("a + b"));
        assertEquals("obj.constructor", JsConstRepair.toLet("obj.constructor"));
    }

    // The compile-time free-var scan rejected the chart bodies before the fix; now it
    // parses the rewritten source instead of throwing.
    @Test
    void freeVarScanSurvivesConstRedecl() {
        String src = "for (let i=0;i<n;i++){const v=i; use(v);} "
                   + "for (let i=0;i<n;i++){const v=i; use(v);}";
        assertDoesNotThrow(() -> RhinoFreeVars.collect(src, Collections.emptySet()));
        assertTrue(RhinoFreeVars.collect(src, Collections.emptySet()).contains("n"));
    }
}
