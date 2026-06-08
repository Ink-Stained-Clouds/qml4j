package io.github.timer_err.qml4j.compiler.bytecode.asm;

// JVM internal names and type descriptors for the engine classes the compiler
// references when emitting bytecode. Centralised so emitters share one spelling
// of each name instead of re-declaring string literals.
public final class Descriptors {

    private Descriptors() {}

    public static final String PROPERTY_INTERNAL = "io/github/timer_err/qml4j/engine/binding/Property";
    public static final String PROPERTY_DESC = "L" + PROPERTY_INTERNAL + ";";
    public static final String BINDING_INTERNAL = "io/github/timer_err/qml4j/engine/binding/Binding";
    public static final String SIGNAL_INTERNAL = "io/github/timer_err/qml4j/engine/Signal";
    public static final String SIGNAL_DESC = "L" + SIGNAL_INTERNAL + ";";
    public static final String SIGNAL_HANDLER_INTERNAL = "io/github/timer_err/qml4j/engine/SignalHandler";
    public static final String SIGNAL_HANDLER_DESC = "L" + SIGNAL_HANDLER_INTERNAL + ";";
    public static final String LIST_INTERNAL = "java/util/List";
    public static final String LIST_DESC = "L" + LIST_INTERNAL + ";";
    public static final String SINK_INTERNAL = "io/github/timer_err/qml4j/engine/PropertyChangeSink";
    public static final String QOBJECT_INTERNAL = "io/github/timer_err/qml4j/engine/QObject";
    public static final String QOBJECT_DESC = "L" + QOBJECT_INTERNAL + ";";
    public static final String DELEGATE_FACTORY_INTERNAL = "io/github/timer_err/qml4j/engine/DelegateFactory";
    public static final String DELEGATE_HOST_INTERNAL = "io/github/timer_err/qml4j/engine/DelegateHost";
    public static final String SIGNAL_RELAY_INTERNAL = "io/github/timer_err/qml4j/engine/SignalRelay";
    public static final String COMPONENT_INTERNAL = "io/github/timer_err/qml4j/render/items/view/Component";

    // Generic signature for a `property Component x` field, so cross-component resolution
    // (an object assigned to it from another file) can detect the Component type and apply
    // Qt's implicit Component wrapping. Plain Property fields carry no signature.
    public static final String COMPONENT_PROPERTY_SIGNATURE =
        "L" + PROPERTY_INTERNAL + "<L" + COMPONENT_INTERNAL + ";>;";

    // The field generic signature for a declared property of QML `typeName`, or null when
    // no signature is needed (only Component is special today).
    public static String propertyFieldSignature(String typeName) {
        return "Component".equals(typeName) ? COMPONENT_PROPERTY_SIGNATURE : null;
    }
}
