package io.qml4j.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTest {

    @Test
    void getSetAndListener() {
        Property<Integer> p = new Property<>(0);
        List<Integer> seen = new ArrayList<>();
        p.addListener(seen::add);
        p.set(1);
        p.set(1);
        p.set(2);
        assertEquals(List.of(1, 2), seen);
        assertEquals(2, p.peek());
    }

    @Test
    void bindInitialValue() {
        Property<Integer> a = new Property<>(3);
        Property<Integer> b = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() * 2; }
        });
        assertEquals(6, b.peek());
        assertTrue(b.isBound());
    }

    @Test
    void depChangePropagates() {
        Property<Integer> a = new Property<>(3);
        Property<Integer> b = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() + 1; }
        });
        a.set(10);
        assertEquals(11, b.peek());
    }

    @Test
    void transitiveDependency() {
        Property<Integer> a = new Property<>(1);
        Property<Integer> b = new Property<>(0);
        Property<Integer> c = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() + 1; }
        });
        c.bind(new Binding() {
            @Override public Object evaluate() { return b.get() * 10; }
        });
        assertEquals(20, c.peek());
        a.set(5);
        assertEquals(60, c.peek());
    }

    @Test
    void unbindStopsPropagation() {
        Property<Integer> a = new Property<>(1);
        Property<Integer> b = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() + 100; }
        });
        assertEquals(101, b.peek());
        b.unbind();
        assertFalse(b.isBound());
        a.set(50);
        assertEquals(101, b.peek());
    }

    @Test
    void setClearsBinding() {
        Property<Integer> a = new Property<>(1);
        Property<Integer> b = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() + 100; }
        });
        assertEquals(101, b.peek());
        b.set(999);
        assertFalse(b.isBound());
        a.set(50);
        assertEquals(999, b.peek());
    }

    @Test
    void dynamicDependencyTracking() {
        Property<Boolean> cond = new Property<>(true);
        Property<Integer> x = new Property<>(10);
        Property<Integer> y = new Property<>(20);
        Property<Integer> out = new Property<>(0);
        AtomicInteger evals = new AtomicInteger();
        out.bind(new Binding() {
            @Override public Object evaluate() {
                evals.incrementAndGet();
                return cond.get() ? x.get() : y.get();
            }
        });
        assertEquals(10, out.peek());

        // y is NOT yet a dependency — changing it should not re-eval
        int evalsBefore = evals.get();
        y.set(99);
        assertEquals(evalsBefore, evals.get());
        assertEquals(10, out.peek());

        // flip cond — re-evals, drops x, picks up y
        cond.set(false);
        assertEquals(99, out.peek());

        // now x is NOT a dep
        int after = evals.get();
        x.set(1234);
        assertEquals(after, evals.get());
        assertEquals(99, out.peek());

        // y IS a dep
        y.set(42);
        assertEquals(42, out.peek());
    }

    @Test
    void cycleDetected() {
        Property<Integer> a = new Property<>(0);
        Property<Integer> b = new Property<>(0);
        b.bind(new Binding() {
            @Override public Object evaluate() { return a.get() + 1; }
        });
        assertThrows(IllegalStateException.class, () ->
            a.bind(new Binding() {
                @Override public Object evaluate() { return b.get() + 1; }
            })
        );
    }

    @Test
    void getOutsideBindingDoesNotLeak() {
        Property<Integer> a = new Property<>(7);
        // simply ensure no exception, and value returned
        assertEquals(7, a.get());
    }

    @Test
    void invalidationListenerFiresOnChange() {
        Property<Integer> a = new Property<>(0);
        AtomicInteger hits = new AtomicInteger();
        a.addInvalidationListener(hits::incrementAndGet);
        a.set(1);
        a.set(1);
        a.set(2);
        assertEquals(2, hits.get());
    }
}
