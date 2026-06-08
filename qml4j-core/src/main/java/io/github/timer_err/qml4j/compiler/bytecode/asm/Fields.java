package io.github.timer_err.qml4j.compiler.bytecode.asm;

import io.github.timer_err.qml4j.engine.QmlDefaultList;
import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

// Reflective lookup of the public Property/Signal/List fields the compiler binds
// against. Pure and stateless: given a host type and a member name, find (or fail
// to find) the field, with the Property/Signal/List type checks the emitter needs.
public final class Fields {

    private Fields() {}

    public static Field findPropertyField(Class<?> outerType, String name) {
        Field f;
        try {
            f = outerType.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "no public field '" + name + "' on " + outerType.getName());
        }
        if (!Property.class.isAssignableFrom(f.getType())) {
            throw new IllegalArgumentException(
                "field '" + name + "' on " + outerType.getName() + " is not a Property");
        }
        return f;
    }

    // Internal name of the class owning `propName`'s Property field -- a declared
    // property's registered owner, or the declaring class of an inherited field. Null
    // if the property has no field (so the Rhino IIFE path is skipped).
    public static String propFieldOwnerOrNull(Class<?> type, String propName,
                                              Map<String, String> declaredProps) {
        if (declaredProps.containsKey(propName)) return declaredProps.get(propName);
        Field f = findPropertyFieldOrNull(type, propName);
        return f == null ? null : Type.getInternalName(f.getDeclaringClass());
    }

    public static Field findPropertyFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return Property.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static Field findListFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return List.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static Field findSignalFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return Signal.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static Field findFieldOrNull(Class<?> type, String name) {
        try { return type.getField(name); }
        catch (NoSuchFieldException e) { return null; }
    }

    public static String defaultListFieldFor(Class<?> type) {
        QmlDefaultList ann = type.getAnnotation(QmlDefaultList.class);
        return ann != null ? ann.value() : "children";
    }

    public static String defaultParentFieldFor(Class<?> type) {
        QmlDefaultList ann = type.getAnnotation(QmlDefaultList.class);
        if (ann == null || ann.parentField().isEmpty()) return null;
        return ann.parentField();
    }

    // java.lang.reflect.Type is FQN'd here: its simple name clashes with the
    // imported org.objectweb.asm.Type used for bytecode emission.
    public static boolean listAcceptsElement(Field listField, Class<?> childType) {
        java.lang.reflect.Type gt = listField.getGenericType();
        if (!(gt instanceof java.lang.reflect.ParameterizedType)) return true;
        java.lang.reflect.Type[] args = ((java.lang.reflect.ParameterizedType) gt).getActualTypeArguments();
        if (args.length == 0) return true;
        java.lang.reflect.Type arg = args[0];
        Class<?> elemClass;
        if (arg instanceof Class) {
            elemClass = (Class<?>) arg;
        } else if (arg instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.Type raw = ((java.lang.reflect.ParameterizedType) arg).getRawType();
            elemClass = (raw instanceof Class) ? (Class<?>) raw : Object.class;
        } else {
            return true;
        }
        return elemClass.isAssignableFrom(childType);
    }
}
