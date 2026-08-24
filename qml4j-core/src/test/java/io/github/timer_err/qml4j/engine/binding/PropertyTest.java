package io.github.timer_err.qml4j.engine.binding;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTest {

    @Test
    void listenerStorageIsLazyAndReleasedWhenEmpty() throws Exception {
        Property<Integer> p = new Property<>(0);
        var valueField = Property.class.getDeclaredField("valueListeners");
        var invalidationField = Property.class.getDeclaredField("invalidationListeners");
        var unsubscribeField = Property.class.getDeclaredField("bindingUnsubscribes");
        valueField.setAccessible(true);
        invalidationField.setAccessible(true);
        unsubscribeField.setAccessible(true);

        assertEquals(null, valueField.get(p));
        assertEquals(null, invalidationField.get(p));
        assertEquals(null, unsubscribeField.get(p));

        Consumer<Integer> valueListener = ignored -> {};
        Runnable invalidationListener = () -> {};
        p.addListener(valueListener);
        p.addInvalidationListener(invalidationListener);
        p.removeListener(valueListener);
        p.removeInvalidationListener(invalidationListener);

        assertEquals(null, valueField.get(p));
        assertEquals(null, invalidationField.get(p));
    }

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

    // Re-delivering the same number in a different box (Long 5 -> Double 5.0) is not a
    // change: it must not fire, or a layout binding re-delivering a parent size in a
    // different numeric type oscillates forever (the ScrollBar implicitHeight churn).
    @Test
    void numericReboxDoesNotFire() {
        Property<Object> p = new Property<>(5L);
        AtomicInteger hits = new AtomicInteger();
        p.addInvalidationListener(hits::incrementAndGet);
        p.set(5.0);    // same magnitude, different box
        p.set(5);      // Integer 5
        assertEquals(0, hits.get(), "re-boxing the same number is not a change");
        p.set(6.0);
        assertEquals(1, hits.get(), "a real magnitude change fires");
    }

    @Test
    void writeInterceptorReceivesUserSetAndBindingPreserved() {
        Property<Integer> src = new Property<>(0);
        Property<Integer> p = new Property<>(0);
        p.bind(new Binding() { @Override public Object evaluate() { return src.get() * 10; } });
        assertEquals(0, p.peek());

        List<Integer> seen = new ArrayList<>();
        p.setInterceptor((prop, val) -> {
            seen.add(val);
            prop.setBypassInterceptor(val);
        });

        src.set(3);
        assertEquals(30, p.peek());
        assertEquals(List.of(30), seen);
        assertTrue(p.isBound(), "binding must survive an intercepted re-eval");

        src.set(4);
        assertEquals(40, p.peek());
        assertEquals(List.of(30, 40), seen);
        assertTrue(p.isBound());
    }

    @Test
    void writeInterceptorOnUserSetClearsBinding() {
        Property<Integer> src = new Property<>(0);
        Property<Integer> p = new Property<>(0);
        p.bind(new Binding() { @Override public Object evaluate() { return src.get(); } });
        p.setInterceptor((prop, val) -> prop.setBypassInterceptor(val));

        p.set(99);
        assertFalse(p.isBound(), "user set() must still clear binding even with interceptor");
        assertEquals(99, p.peek());
        src.set(5);
        assertEquals(99, p.peek());
    }

    @Test
    void setBypassInterceptorDoesNotInvokeInterceptor() {
        Property<Integer> p = new Property<>(0);
        AtomicInteger hits = new AtomicInteger();
        p.setInterceptor((prop, val) -> {
            hits.incrementAndGet();
            prop.setBypassInterceptor(val);
        });
        p.setBypassInterceptor(7);
        assertEquals(7, p.peek());
        assertEquals(0, hits.get());
    }
}
