package io.qml4j.compiler.bytecode;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExpressionCodegen {

    static final String HELPERS_INTERNAL = "io/qml4j/engine/RuntimeHelpers";
    static final String PROPERTY_INTERNAL = "io/qml4j/engine/binding/Property";
    private static final String BINARY_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String UNARY_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";

    static final class AliasRef {
        final String targetId;
        final String targetProperty;
        AliasRef(String targetId, String targetProperty) {
            this.targetId = targetId;
            this.targetProperty = targetProperty;
        }
    }

    interface BindingEmitter {
        String emit(Ast.Expression expr);
    }

    interface ArrowEmitter {
        String emit(Ast.ArrowFunctionExpr fn);
    }

    private final String outerInternal;
    private final String bindingInternal;
    private final Class<?> outerType;
    private final String componentInternal;
    private final Map<String, Class<? extends QObject>> idTypes;
    private final Map<String, Integer> signalParams;
    private final Map<String, Integer> localVars;
    private final Map<String, String> declaredProps;
    private final Map<String, AliasRef> aliases;
    private final Map<String, Integer> rootFunctions;
    private final boolean selfReceiver;
    private BindingEmitter bindingEmitter;
    private ArrowEmitter arrowEmitter;

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType) {
        this(outerInternal, bindingInternal, outerType, null,
             Collections.<String, Class<? extends QObject>>emptyMap(),
             Collections.<String, Integer>emptyMap(),
             new LinkedHashMap<String, Integer>(),
             Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap(),
             Collections.<String, Integer>emptyMap(),
             false);
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes) {
        this(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
             Collections.<String, Integer>emptyMap(),
             new LinkedHashMap<String, Integer>(),
             Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap(),
             Collections.<String, Integer>emptyMap(),
             false);
    }

    static ExpressionCodegen forBinding(String outerInternal, String bindingInternal, Class<?> outerType,
                                        String componentInternal,
                                        Map<String, Class<? extends QObject>> idTypes,
                                        Map<String, String> declaredProps,
                                        Map<String, AliasRef> aliases,
                                        Map<String, Integer> rootFunctions) {
        return new ExpressionCodegen(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
                                     Collections.<String, Integer>emptyMap(),
                                     new LinkedHashMap<String, Integer>(),
                                     declaredProps, aliases, rootFunctions, false);
    }

    static ExpressionCodegen forArrow(String outerInternal, String arrowInternal, Class<?> outerType,
                                      String componentInternal,
                                      Map<String, Class<? extends QObject>> idTypes,
                                      Map<String, Integer> directParams,
                                      Map<String, String> declaredProps,
                                      Map<String, AliasRef> aliases,
                                      Map<String, Integer> rootFunctions) {
        return new ExpressionCodegen(outerInternal, arrowInternal, outerType, componentInternal, idTypes,
                                     Collections.<String, Integer>emptyMap(),
                                     new LinkedHashMap<>(directParams),
                                     declaredProps, aliases, rootFunctions, false);
    }

    static ExpressionCodegen forFunction(String componentInternal, Class<?> componentType,
                                         Map<String, Class<? extends QObject>> idTypes,
                                         Map<String, Integer> directParams,
                                         Map<String, String> declaredProps,
                                         Map<String, AliasRef> aliases,
                                         Map<String, Integer> rootFunctions) {
        Map<String, Integer> locals = new LinkedHashMap<>(directParams);
        return new ExpressionCodegen(componentInternal, componentInternal, componentType, componentInternal,
                                     idTypes, Collections.<String, Integer>emptyMap(),
                                     locals, declaredProps, aliases, rootFunctions, true);
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes,
                      Map<String, Integer> signalParams,
                      Map<String, Integer> localVars,
                      Map<String, String> declaredProps,
                      Map<String, AliasRef> aliases,
                      Map<String, Integer> rootFunctions) {
        this(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
             signalParams, localVars, declaredProps, aliases, rootFunctions, false);
    }

    void setBindingEmitter(BindingEmitter emitter) {
        this.bindingEmitter = emitter;
    }

    void setArrowEmitter(ArrowEmitter emitter) {
        this.arrowEmitter = emitter;
    }

    ArrowEmitter arrowEmitter() { return arrowEmitter; }

    private ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                              String componentInternal,
                              Map<String, Class<? extends QObject>> idTypes,
                              Map<String, Integer> signalParams,
                              Map<String, Integer> localVars,
                              Map<String, String> declaredProps,
                              Map<String, AliasRef> aliases,
                              Map<String, Integer> rootFunctions,
                              boolean selfReceiver) {
        this.outerInternal = outerInternal;
        this.bindingInternal = bindingInternal;
        this.outerType = outerType;
        this.componentInternal = componentInternal;
        this.idTypes = idTypes != null ? idTypes : Collections.<String, Class<? extends QObject>>emptyMap();
        this.signalParams = signalParams != null ? signalParams : Collections.<String, Integer>emptyMap();
        this.localVars = localVars != null ? localVars : new LinkedHashMap<String, Integer>();
        this.declaredProps = declaredProps != null ? declaredProps : Collections.<String, String>emptyMap();
        this.aliases = aliases != null ? aliases : Collections.<String, AliasRef>emptyMap();
        this.rootFunctions = rootFunctions != null ? rootFunctions : Collections.<String, Integer>emptyMap();
        this.selfReceiver = selfReceiver;
    }

    private void loadOuter(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (!selfReceiver) {
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
        }
    }

    private void loadRoot(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (!selfReceiver) {
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "root", "L" + componentInternal + ";");
        }
    }

    Map<String, Integer> localVars() { return localVars; }

    void emit(MethodVisitor mv, Ast.Expression e) {
        if (e instanceof Ast.LiteralExpr) {
            emitLiteral(mv, (Ast.LiteralExpr) e);
        } else if (e instanceof Ast.IdentifierExpr) {
            emitIdentifier(mv, (Ast.IdentifierExpr) e);
        } else if (e instanceof Ast.MemberExpr) {
            emitMember(mv, (Ast.MemberExpr) e);
        } else if (e instanceof Ast.UnaryExpr) {
            emitUnary(mv, (Ast.UnaryExpr) e);
        } else if (e instanceof Ast.BinaryExpr) {
            emitBinary(mv, (Ast.BinaryExpr) e);
        } else if (e instanceof Ast.CondExpr) {
            emitCond(mv, (Ast.CondExpr) e);
        } else if (e instanceof Ast.AssignmentExpr) {
            emitAssignment(mv, (Ast.AssignmentExpr) e);
        } else if (e instanceof Ast.CallExpr) {
            emitCall(mv, (Ast.CallExpr) e);
        } else if (e instanceof Ast.IndexExpr) {
            emitIndex(mv, (Ast.IndexExpr) e);
        } else if (e instanceof Ast.ArrayLitExpr) {
            emitArrayLit(mv, (Ast.ArrayLitExpr) e);
        } else if (e instanceof Ast.ObjectLitExpr) {
            emitObjectLit(mv, (Ast.ObjectLitExpr) e);
        } else if (e instanceof Ast.TemplateLiteralExpr) {
            emitTemplateLiteral(mv, (Ast.TemplateLiteralExpr) e);
        } else if (e instanceof Ast.ArrowFunctionExpr) {
            emitArrowFunction(mv, (Ast.ArrowFunctionExpr) e);
        } else if (e instanceof Ast.SpreadExpr) {
            throw new IllegalStateException("spread expression outside of call/array context");
        } else {
            throw new IllegalStateException("unknown expression: " + e.getClass().getName());
        }
    }

    private void emitIndex(MethodVisitor mv, Ast.IndexExpr ix) {
        emit(mv, ix.target);
        emit(mv, ix.index);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "readIndex",
                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    private void emitArrayLit(MethodVisitor mv, Ast.ArrayLitExpr a) {
        emitArgsArray(mv, a.elements);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "makeArray",
                           "([Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    private boolean hasSpread(java.util.List<Ast.Expression> args) {
        for (Ast.Expression a : args) if (a instanceof Ast.SpreadExpr) return true;
        return false;
    }

    private void emitArgsArray(MethodVisitor mv, java.util.List<Ast.Expression> args) {
        if (!hasSpread(args)) {
            pushInt(mv, args.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            for (int i = 0; i < args.size(); i++) {
                mv.visitInsn(Opcodes.DUP);
                pushInt(mv, i);
                emit(mv, args.get(i));
                mv.visitInsn(Opcodes.AASTORE);
            }
            return;
        }
        mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        for (Ast.Expression a : args) {
            if (a instanceof Ast.SpreadExpr) {
                mv.visitInsn(Opcodes.DUP);
                emit(mv, ((Ast.SpreadExpr) a).target);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                                   "spreadInto",
                                   "(Ljava/util/ArrayList;Ljava/lang/Object;)V", false);
            } else {
                mv.visitInsn(Opcodes.DUP);
                emit(mv, a);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList",
                                   "add", "(Ljava/lang/Object;)Z", false);
                mv.visitInsn(Opcodes.POP);
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "listToArray",
                           "(Ljava/util/ArrayList;)[Ljava/lang/Object;", false);
    }

    private void emitTemplateLiteral(MethodVisitor mv, Ast.TemplateLiteralExpr t) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                           "<init>", "()V", false);
        for (int i = 0; i < t.rawParts.size(); i++) {
            String part = t.rawParts.get(i);
            if (!part.isEmpty()) {
                mv.visitLdcInsn(part);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                   "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            if (i < t.exprs.size()) {
                emit(mv, t.exprs.get(i));
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                                   "stringify", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                   "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                           "toString", "()Ljava/lang/String;", false);
    }

    private void emitArrowFunction(MethodVisitor mv, Ast.ArrowFunctionExpr a) {
        if (arrowEmitter == null) {
            throw new IllegalStateException("arrow function requires ArrowEmitter in this codegen scope");
        }
        String childInternal = arrowEmitter.emit(a);
        mv.visitTypeInsn(Opcodes.NEW, childInternal);
        mv.visitInsn(Opcodes.DUP);
        loadOuter(mv);
        loadRoot(mv);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>",
                           "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
    }

    private void emitObjectLit(MethodVisitor mv, Ast.ObjectLitExpr o) {
        int n = o.keys.size();
        pushInt(mv, n);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < n; i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            mv.visitLdcInsn(o.keys.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
        pushInt(mv, n);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < n; i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            emit(mv, o.values.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "makeObject",
                           "([Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    private void emitLiteral(MethodVisitor mv, Ast.LiteralExpr lit) {
        switch (lit.kind) {
            case INT:
                mv.visitLdcInsn((Long) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                                   "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case FLOAT:
                mv.visitLdcInsn((Double) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double",
                                   "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case STRING:
                mv.visitLdcInsn((String) lit.value);
                break;
            case BOOL:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean",
                                  ((Boolean) lit.value) ? "TRUE" : "FALSE",
                                  "Ljava/lang/Boolean;");
                break;
            case NULL:
            case UNDEFINED:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
            default:
                throw new IllegalStateException("literal kind: " + lit.kind);
        }
    }

    private void emitIdentifier(MethodVisitor mv, Ast.IdentifierExpr id) {
        Integer localSlot = localVars.get(id.name);
        if (localSlot != null) {
            mv.visitVarInsn(Opcodes.ALOAD, localSlot);
            return;
        }
        Integer paramSlot = signalParams.get(id.name);
        if (paramSlot != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            pushInt(mv, paramSlot);
            mv.visitInsn(Opcodes.AALOAD);
            return;
        }
        AliasRef alias = aliases.get(id.name);
        if (alias != null) {
            emit(mv, new Ast.MemberExpr(new Ast.IdentifierExpr(alias.targetId), alias.targetProperty));
            return;
        }
        String declaredOwner = declaredProps.get(id.name);
        if (declaredOwner != null) {
            loadDeclaredPropertyField(mv, id.name, declaredOwner);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                               "get", "()Ljava/lang/Object;", false);
            return;
        }
        Class<? extends QObject> idType = idTypes.get(id.name);
        if (idType != null && componentInternal != null) {
            loadRoot(mv);
            mv.visitFieldInsn(Opcodes.GETFIELD, componentInternal, id.name,
                              "L" + Type.getInternalName(idType) + ";");
            return;
        }
        if (QmlCompiler.inDelegateScope()
            && ("index".equals(id.name) || "modelData".equals(id.name))) {
            loadOuter(mv);
            mv.visitLdcInsn(id.name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                               "delegateContext",
                               "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                               false);
            return;
        }
        QmlCompiler.tryResolveType(id.name);
        Class<? extends QObject> singleton = QmlCompiler.currentSingletonClass(id.name);
        if (singleton != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(singleton),
                               "__instance", "()Ljava/lang/Object;", false);
            return;
        }
        // Inside a delegate, an unresolved identifier may be a delegate-local id
        // (or a delegate-scope name): resolve at runtime by walking the parent
        // chain to the DelegateRoot. Only when it's not a property of this object.
        if (QmlCompiler.inDelegateScope() && !hasPropertyField(outerType, id.name)) {
            loadOuter(mv);
            mv.visitLdcInsn(id.name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, "delegateContext",
                               "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
            return;
        }
        Field f = findPropertyField(outerType, id.name);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        loadOuter(mv);
        mv.visitFieldInsn(Opcodes.GETFIELD, declOwner, id.name, "L" + PROPERTY_INTERNAL + ";");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "get", "()Ljava/lang/Object;", false);
    }

    private static boolean hasPropertyField(Class<?> outerType, String name) {
        try {
            return Property.class.isAssignableFrom(outerType.getField(name).getType());
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private void loadDeclaredPropertyField(MethodVisitor mv, String name, String ownerInternal) {
        if (componentInternal != null && ownerInternal.equals(componentInternal)) {
            loadRoot(mv);
        } else {
            loadOuter(mv);
            mv.visitTypeInsn(Opcodes.CHECKCAST, ownerInternal);
        }
        mv.visitFieldInsn(Opcodes.GETFIELD, ownerInternal, name, "L" + PROPERTY_INTERNAL + ";");
    }

    private void emitMember(MethodVisitor mv, Ast.MemberExpr m) {
        Long enumVal = enumConstant(m);
        if (enumVal != null) {
            mv.visitLdcInsn(enumVal.longValue());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                               "valueOf", "(J)Ljava/lang/Long;", false);
            return;
        }
        emit(mv, m.target);
        mv.visitLdcInsn(m.property);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "readMember",
                           "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
    }

    // QML enum member access (Qt.Vertical, Text.AlignVCenter, Font.Bold,
    // Easing.OutQuad). Resolved to int constants at compile time. Only when the
    // namespace identifier is a known enum namespace AND is not shadowed by a
    // local var / signal param / alias / declared property / id.
    private Long enumConstant(Ast.MemberExpr m) {
        if (!(m.target instanceof Ast.IdentifierExpr)) return null;
        String ns = ((Ast.IdentifierExpr) m.target).name;
        Map<String, Long> members = ENUMS.get(ns);
        if (members == null) return null;
        if (localVars.containsKey(ns) || signalParams.containsKey(ns)
            || aliases.containsKey(ns) || declaredProps.containsKey(ns)
            || idTypes.containsKey(ns)) {
            return null;
        }
        return members.get(m.property);
    }

    private static final Map<String, Map<String, Long>> ENUMS = buildEnums();

    private static Map<String, Map<String, Long>> buildEnums() {
        Map<String, Map<String, Long>> e = new HashMap<>();

        Map<String, Long> qt = new HashMap<>();
        qt.put("Horizontal", 1L);
        qt.put("Vertical", 2L);
        qt.put("NoButton", 0L);
        qt.put("LeftButton", 1L);
        qt.put("RightButton", 2L);
        qt.put("MiddleButton", 4L);
        qt.put("AlignLeft", 1L);
        qt.put("AlignRight", 2L);
        qt.put("AlignHCenter", 4L);
        qt.put("AlignJustify", 8L);
        qt.put("AlignTop", 32L);
        qt.put("AlignBottom", 64L);
        qt.put("AlignVCenter", 128L);
        qt.put("AlignBaseline", 256L);
        qt.put("AlignCenter", 132L); // AlignVCenter | AlignHCenter
        qt.put("ArrowCursor", 0L);
        qt.put("IBeamCursor", 4L);
        qt.put("PointingHandCursor", 13L);
        e.put("Qt", qt);

        Map<String, Long> text = new HashMap<>();
        text.put("AlignLeft", 1L);
        text.put("AlignRight", 2L);
        text.put("AlignHCenter", 4L);
        text.put("AlignJustify", 8L);
        text.put("AlignTop", 32L);
        text.put("AlignBottom", 64L);
        text.put("AlignVCenter", 128L);
        text.put("NoWrap", 0L);
        text.put("WordWrap", 1L);
        text.put("WrapAnywhere", 3L);
        text.put("Wrap", 4L);
        text.put("ElideNone", 0L);
        text.put("ElideLeft", 1L);
        text.put("ElideMiddle", 2L);
        text.put("ElideRight", 3L);
        e.put("Text", text);

        Map<String, Long> font = new HashMap<>();
        font.put("Thin", 0L);
        font.put("ExtraLight", 12L);
        font.put("Light", 25L);
        font.put("Normal", 50L);
        font.put("Medium", 57L);
        font.put("DemiBold", 63L);
        font.put("Bold", 75L);
        font.put("ExtraBold", 81L);
        font.put("Black", 87L);
        font.put("MixedCase", 0L);
        font.put("AllUppercase", 1L);
        font.put("AllLowercase", 2L);
        font.put("SmallCaps", 3L);
        font.put("Capitalize", 4L);
        e.put("Font", font);

        // QEasingCurve::Type ordinals.
        Map<String, Long> easing = new HashMap<>();
        easing.put("Linear", 0L);
        easing.put("InQuad", 1L);
        easing.put("OutQuad", 2L);
        easing.put("InOutQuad", 3L);
        easing.put("OutInQuad", 4L);
        easing.put("InCubic", 5L);
        easing.put("OutCubic", 6L);
        easing.put("InOutCubic", 7L);
        easing.put("OutInCubic", 8L);
        easing.put("InQuart", 9L);
        easing.put("OutQuart", 10L);
        easing.put("InOutQuart", 11L);
        easing.put("InSine", 13L);
        easing.put("OutSine", 14L);
        easing.put("InOutSine", 15L);
        easing.put("InBack", 29L);
        easing.put("OutBack", 30L);
        easing.put("InOutBack", 31L);
        e.put("Easing", easing);

        return e;
    }

    private void emitUnary(MethodVisitor mv, Ast.UnaryExpr u) {
        emit(mv, u.operand);
        String helper;
        switch (u.op) {
            case "-": helper = "neg"; break;
            case "+": helper = "pos"; break;
            case "!": helper = "not"; break;
            default: throw new IllegalStateException("unary op: " + u.op);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, helper, UNARY_DESC, false);
    }

    private void emitBinary(MethodVisitor mv, Ast.BinaryExpr b) {
        emit(mv, b.left);
        emit(mv, b.right);
        String helper = binaryHelperName(b.op);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, helper, BINARY_DESC, false);
    }

    private static String binaryHelperName(String op) {
        switch (op) {
            case "+":   return "add";
            case "-":   return "sub";
            case "*":   return "mul";
            case "/":   return "div";
            case "%":   return "mod";
            case "==":  return "eq";
            case "!=":  return "neq";
            case "===": return "eqStrict";
            case "!==": return "neqStrict";
            case "<":   return "lt";
            case "<=":  return "le";
            case ">":   return "gt";
            case ">=":  return "ge";
            case "&&":  return "and";
            case "||":  return "or";
            case "&":   return "bitAnd";
            case "|":   return "bitOr";
            case "^":   return "bitXor";
            default: throw new IllegalStateException("binary op: " + op);
        }
    }

    private void emitAssignment(MethodVisitor mv, Ast.AssignmentExpr a) {
        if (a.target instanceof Ast.IndexExpr) {
            Ast.IndexExpr ix = (Ast.IndexExpr) a.target;
            emit(mv, ix.target);
            emit(mv, ix.index);
            emit(mv, a.value);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                               "writeIndex",
                               "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                               false);
            return;
        }
        if (a.target instanceof Ast.MemberExpr) {
            Ast.MemberExpr m = (Ast.MemberExpr) a.target;
            emit(mv, m.target);
            mv.visitLdcInsn(m.property);
            emit(mv, a.value);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                               "writeMember",
                               "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                               false);
        } else if (a.target instanceof Ast.IdentifierExpr) {
            String name = ((Ast.IdentifierExpr) a.target).name;
            Integer localSlot = localVars.get(name);
            if (localSlot != null) {
                emit(mv, a.value);
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ASTORE, localSlot);
                return;
            }
            AliasRef alias = aliases.get(name);
            if (alias != null) {
                Ast.MemberExpr m = new Ast.MemberExpr(new Ast.IdentifierExpr(alias.targetId), alias.targetProperty);
                emitAssignment(mv, new Ast.AssignmentExpr(m, a.value));
                return;
            }
            String declaredOwner = declaredProps.get(name);
            if (declaredOwner != null) {
                loadDeclaredPropertyField(mv, name, declaredOwner);
                emit(mv, a.value);
                mv.visitInsn(Opcodes.DUP_X1);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                   "set", "(Ljava/lang/Object;)V", false);
                return;
            }
            Field f = findPropertyField(outerType, name);
            String declOwner = Type.getInternalName(f.getDeclaringClass());
            loadOuter(mv);
            mv.visitFieldInsn(Opcodes.GETFIELD, declOwner, name, "L" + PROPERTY_INTERNAL + ";");
            emit(mv, a.value);
            mv.visitInsn(Opcodes.DUP_X1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                               "set", "(Ljava/lang/Object;)V", false);
        } else {
            throw new UnsupportedOperationException(
                "assignment target must be identifier or member access, got " + a.target.getClass().getSimpleName());
        }
    }

    private void emitCall(MethodVisitor mv, Ast.CallExpr c) {
        if (c.callee instanceof Ast.IdentifierExpr) {
            emitBareNameCall(mv, (Ast.IdentifierExpr) c.callee, c.args);
            return;
        }
        if (c.callee instanceof Ast.ArrowFunctionExpr) {
            // Inline-invoked function expression, e.g. an IIFE block binding.
            emit(mv, c.callee);
            emitArgsArray(mv, c.args);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, "callValue",
                               "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            return;
        }
        if (!(c.callee instanceof Ast.MemberExpr)) {
            throw new UnsupportedOperationException(
                "only method calls of the form receiver.method(...) are supported");
        }
        Ast.MemberExpr m = (Ast.MemberExpr) c.callee;
        if (tryEmitQtCall(mv, m, c.args)) return;
        if (tryEmitMathCall(mv, m, c.args)) return;
        emit(mv, m.target);
        mv.visitLdcInsn(m.property);
        emitArgsArray(mv, c.args);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "callMethod",
                           "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    // The JS Math global: Math.max(...), Math.min(...), Math.floor(x), etc.
    // Resolved at compile time unless `Math` is shadowed by a local binding.
    private boolean tryEmitMathCall(MethodVisitor mv, Ast.MemberExpr m, java.util.List<Ast.Expression> args) {
        if (!(m.target instanceof Ast.IdentifierExpr)) return false;
        String ns = ((Ast.IdentifierExpr) m.target).name;
        if (!"Math".equals(ns)) return false;
        if (localVars.containsKey(ns) || signalParams.containsKey(ns)
            || aliases.containsKey(ns) || declaredProps.containsKey(ns)
            || idTypes.containsKey(ns)) {
            return false;
        }
        mv.visitLdcInsn(m.property);
        emitArgsArray(mv, args);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, "callMath",
                           "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        return true;
    }

    private boolean tryEmitQtCall(MethodVisitor mv, Ast.MemberExpr m, java.util.List<Ast.Expression> args) {
        if (!(m.target instanceof Ast.IdentifierExpr)) return false;
        if (!"Qt".equals(((Ast.IdentifierExpr) m.target).name)) return false;
        switch (m.property) {
            case "binding":
                emitQtBinding(mv, args);
                return true;
            case "callLater":
                emitQtCallLater(mv, args);
                return true;
            case "rgba":
                emitQtFourArg(mv, args, "qtRgba");
                return true;
            case "hsla":
                emitQtFourArg(mv, args, "qtHsla");
                return true;
            case "color":
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Qt.color expects 1 argument, got " + args.size());
                }
                emit(mv, args.get(0));
                callQtHelper(mv, "qtColor", "(Ljava/lang/Object;)Ljava/lang/Object;");
                return true;
            default:
                throw new UnsupportedOperationException("Qt." + m.property + " is not supported");
        }
    }

    private void emitQtBinding(MethodVisitor mv, java.util.List<Ast.Expression> args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Qt.binding expects 1 argument, got " + args.size());
        }
        if (bindingEmitter == null) {
            throw new IllegalStateException("Qt.binding requires BindingEmitter in this codegen scope");
        }
        String childInternal = bindingEmitter.emit(args.get(0));
        mv.visitTypeInsn(Opcodes.NEW, childInternal);
        mv.visitInsn(Opcodes.DUP);
        loadOuter(mv);
        loadRoot(mv);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>",
                           "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
    }

    private void emitQtCallLater(MethodVisitor mv, java.util.List<Ast.Expression> args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Qt.callLater expects 1 argument, got " + args.size());
        }
        Ast.Expression arg = args.get(0);
        if (arg instanceof Ast.ArrowFunctionExpr) {
            emit(mv, arg);
            callQtHelper(mv, "qtCallLater", "(Ljava/lang/Object;)V");
            pushUndefined(mv);
            return;
        }
        if (arg instanceof Ast.CallExpr
            && ((Ast.CallExpr) arg).callee instanceof Ast.MemberExpr) {
            Ast.MemberExpr inner = (Ast.MemberExpr) ((Ast.CallExpr) arg).callee;
            if (inner.target instanceof Ast.IdentifierExpr
                && "Qt".equals(((Ast.IdentifierExpr) inner.target).name)
                && "binding".equals(inner.property)) {
                emit(mv, arg);
                callQtHelper(mv, "qtCallLater", "(Ljava/lang/Object;)V");
                pushUndefined(mv);
                return;
            }
        }
        if (bindingEmitter == null) {
            throw new IllegalStateException("Qt.callLater requires BindingEmitter in this codegen scope");
        }
        String childInternal = bindingEmitter.emit(arg);
        mv.visitTypeInsn(Opcodes.NEW, childInternal);
        mv.visitInsn(Opcodes.DUP);
        loadOuter(mv);
        loadRoot(mv);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>",
                           "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        callQtHelper(mv, "qtCallLater", "(Ljava/lang/Object;)V");
        pushUndefined(mv);
    }

    private void emitQtFourArg(MethodVisitor mv, java.util.List<Ast.Expression> args, String helper) {
        if (args.size() != 4) {
            throw new IllegalArgumentException("Qt." + helper + " expects 4 arguments, got " + args.size());
        }
        for (Ast.Expression a : args) emit(mv, a);
        callQtHelper(mv, helper,
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    }

    private void callQtHelper(MethodVisitor mv, String name, String desc) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, name, desc, false);
    }

    private void pushUndefined(MethodVisitor mv) {
        mv.visitInsn(Opcodes.ACONST_NULL);
    }

    private void emitBareNameCall(MethodVisitor mv, Ast.IdentifierExpr callee, java.util.List<Ast.Expression> args) {
        Integer slot = localVars.get(callee.name);
        if (slot != null) {
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            emitArgsArray(mv, args);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                               "callValue",
                               "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            return;
        }
        loadOuter(mv);
        loadRoot(mv);
        mv.visitLdcInsn(callee.name);
        emitArgsArray(mv, args);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "callQml",
                           "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    private void emitCond(MethodVisitor mv, Ast.CondExpr c) {
        Label elseL = new Label();
        Label endL = new Label();
        emit(mv, c.cond);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "truthy", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, elseL);
        emit(mv, c.thenBranch);
        mv.visitJumpInsn(Opcodes.GOTO, endL);
        mv.visitLabel(elseL);
        emit(mv, c.elseBranch);
        mv.visitLabel(endL);
    }

    private static void pushInt(MethodVisitor mv, int v) {
        if (v >= 0 && v <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + v);
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, v);
        } else {
            mv.visitLdcInsn(v);
        }
    }

    private static Field findPropertyField(Class<?> outerType, String name) {
        Field f;
        try {
            f = outerType.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "identifier '" + name + "' has no matching Property field on "
                + outerType.getName());
        }
        if (!Property.class.isAssignableFrom(f.getType())) {
            throw new IllegalArgumentException(
                "field '" + name + "' on " + outerType.getName() + " is not a Property");
        }
        return f;
    }
}
