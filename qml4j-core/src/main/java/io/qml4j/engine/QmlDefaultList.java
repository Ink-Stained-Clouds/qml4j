package io.qml4j.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QmlDefaultList {
    String value();
    // For a list alias `default property alias x: inner.data`, the id field of
    // the inner container that default children should be parented to (so their
    // anchors resolve against it, matching where they render). "" = the component.
    String parentField() default "";
}
