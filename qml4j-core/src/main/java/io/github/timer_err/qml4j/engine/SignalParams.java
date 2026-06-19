package io.github.timer_err.qml4j.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records a user-declared signal's parameter names on its compiled {@link Signal}
 * field. The compiler stamps it from a {@code signal foo(int a, string b)}
 * declaration so a handler in another file — which only sees the field via
 * reflection, not the declaring document's compile-time tables — can bind the
 * arguments by name ({@code onFoo: doThing(a, b)}) the same way a same-file
 * handler does.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SignalParams {
    String[] value();
}
