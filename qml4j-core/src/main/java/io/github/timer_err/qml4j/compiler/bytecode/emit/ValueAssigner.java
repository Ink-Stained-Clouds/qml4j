package io.github.timer_err.qml4j.compiler.bytecode.emit;

import io.github.timer_err.qml4j.compiler.bytecode.AliasRef;
import io.github.timer_err.qml4j.compiler.bytecode.Literals;
import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.parser.ast.Ast;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.github.timer_err.qml4j.compiler.bytecode.asm.Bytecode.loadLiteral;
import static io.github.timer_err.qml4j.compiler.bytecode.asm.Descriptors.BINDING_INTERNAL;
import static io.github.timer_err.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.github.timer_err.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_INTERNAL;
import static io.github.timer_err.qml4j.compiler.bytecode.asm.Descriptors.SINK_INTERNAL;
import static io.github.timer_err.qml4j.compiler.bytecode.asm.Fields.findPropertyField;
import static io.github.timer_err.qml4j.compiler.bytecode.emit.BindingEmitter.emitRhinoBindingBind;
import static io.github.timer_err.qml4j.compiler.bytecode.emit.BindingEmitter.pushNewRhinoBinding;
import static io.github.timer_err.qml4j.compiler.bytecode.rhino.RhinoScope.collectAliasesFrom;
import static io.github.timer_err.qml4j.compiler.bytecode.rhino.RhinoScope.collectSingletonsFrom;
import static io.github.timer_err.qml4j.compiler.bytecode.rhino.RhinoScope.require;

// Static helpers for assigning values (literals or expressions) to property
// fields and change sinks in a generated constructor.
public final class ValueAssigner {

    private ValueAssigner() {}

    // Sets property `propName` to a literal value: GET the Property field, push the
    // literal, call set(). No binding — the value is fixed at construction time.
    public static void emitLiteralAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                             int outerLocal, String propName, Ast.LiteralExpr lit) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        loadLiteral(ctor, lit);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "set", "(Ljava/lang/Object;)V", false);
    }

    // Binds the property field ownerInternal.name to a RhinoBinding. Caller has
    // already resolved the ownerInternal — used when the field is a freshly declared
    // property and the owner is known directly without reflection.
    public static void emitDeclaredPropertyBinding(MethodVisitor ctor,
                                                   Class<? extends QObject> outerType,
                                                   int outerLocal,
                                                   String ownerInternal, String name,
                                                   String source,
                                                   Map<String, Class<? extends QObject>> idTypes,
                                                   Map<String, String> declaredProps,
                                                   Map<String, AliasRef> aliases,
                                                   Map<String, Integer> rootFunctions,
                                                   Set<String> customSignals) {
        if (source == null) {
            throw new IllegalArgumentException("binding for '" + name + "' has no captured source");
        }
        require(source, Collections.<String>emptySet(), outerType, idTypes, declaredProps,
                rootFunctions, customSignals, aliases);
        emitRhinoBindingBind(ctor, ownerInternal, name, source, outerLocal, idTypes,
                             collectSingletonsFrom(source), collectAliasesFrom(source, aliases));
    }

    // Reflects propName on outerType to locate its field owner, validates the binding
    // source is available, and wires it to a RhinoBinding. Used for inherited properties
    // whose ownerInternal must be resolved via reflection.
    public static void emitExpressionBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                             int outerLocal, String propName,
                                             String source,
                                             Map<String, Class<? extends QObject>> idTypes,
                                             Map<String, String> declaredProps,
                                             Map<String, AliasRef> aliases,
                                             Map<String, Integer> rootFunctions,
                                             Set<String> customSignals) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        if (source == null) {
            throw new IllegalArgumentException("binding for '" + propName + "' has no captured source");
        }
        require(source, Collections.<String>emptySet(), outerType, idTypes, declaredProps,
                rootFunctions, customSignals, aliases);
        emitRhinoBindingBind(ctor, declOwner, propName, source, outerLocal, idTypes,
                             collectSingletonsFrom(source), collectAliasesFrom(source, aliases));
    }

    // Assigns `name` on a PropertyChangeSink: a literal uses addChange, an expression
    // wraps a RhinoBinding and calls addChangeBinding.
    public static void emitChangeSinkAssignment(MethodVisitor ctor,
                                                Class<? extends QObject> outerType, int outerLocal,
                                                String name, String source,
                                                Map<String, Class<? extends QObject>> idTypes,
                                                Map<String, String> declaredProps,
                                                Map<String, AliasRef> aliases,
                                                Map<String, Integer> rootFunctions,
                                                Set<String> customSignals) {
        Ast.LiteralExpr lit = Literals.parse(source);
        if (lit != null) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitLdcInsn(name);
            loadLiteral(ctor, lit);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                                 "addChange", "(Ljava/lang/String;Ljava/lang/Object;)V", true);
            return;
        }
        if (source == null) {
            throw new IllegalArgumentException("change '" + name + "' has no captured source");
        }
        require(source, Collections.<String>emptySet(), outerType, idTypes, declaredProps,
                rootFunctions, customSignals, aliases);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(name);
        pushNewRhinoBinding(ctor, source, outerLocal, idTypes,
                            collectSingletonsFrom(source), collectAliasesFrom(source, aliases));
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                             "addChangeBinding",
                             "(Ljava/lang/String;L" + BINDING_INTERNAL + ";)V", true);
    }
}
