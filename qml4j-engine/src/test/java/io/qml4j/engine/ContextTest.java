package io.qml4j.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextTest {

    static final class Dummy extends QObject {}

    @Test
    void registerAndLookup() {
        Context c = new Context();
        Dummy d = new Dummy();
        c.registerId("root", d);
        assertSame(d, c.lookupId("root"));
        assertNull(c.lookupId("missing"));
    }

    @Test
    void parentChainLookup() {
        Context parent = new Context();
        Dummy d = new Dummy();
        parent.registerId("root", d);
        Context child = new Context(parent);
        assertSame(d, child.lookupId("root"));
    }

    @Test
    void duplicateIdRejected() {
        Context c = new Context();
        c.registerId("x", new Dummy());
        assertThrows(IllegalStateException.class, () -> c.registerId("x", new Dummy()));
    }

    @Test
    void childShadowsParent() {
        Context parent = new Context();
        Dummy outer = new Dummy();
        Dummy inner = new Dummy();
        parent.registerId("x", outer);
        Context child = new Context(parent);
        child.registerId("x", inner);
        assertSame(inner, child.lookupId("x"));
        assertSame(outer, parent.lookupId("x"));
    }
}
