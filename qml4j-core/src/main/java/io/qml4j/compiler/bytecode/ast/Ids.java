package io.qml4j.compiler.bytecode.ast;

import io.qml4j.compiler.TypeRegistry;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.QObject;
import io.qml4j.parser.ast.Ast;

import java.util.Map;
import java.util.regex.Pattern;

// Resolving object `id:` names from the AST: the local id of one node, and a
// recursive walk collecting every id in a document to its compile-time type
// (delegate-internal ids are excluded -- they live on the delegate instance, not
// the component, so they don't get a component field).
public final class Ids {

    private Ids() {}

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");

    public static String idOf(Ast.ObjectNode obj) {
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.PropertyBinding) {
                Ast.PropertyBinding b = (Ast.PropertyBinding) m;
                if (b.path.size() == 1 && "id".equals(b.path.get(0))) {
                    if (b.value instanceof Ast.ExpressionValue) {
                        String s = ((Ast.ExpressionValue) b.value).source;
                        if (s != null && IDENTIFIER.matcher(s.trim()).matches()) {
                            return s.trim();
                        }
                    }
                    throw new IllegalArgumentException("id value must be a simple identifier");
                }
            }
        }
        return null;
    }

    public static void collectIds(Ast.ObjectNode obj, TypeRegistry registry,
                                  Map<String, Class<? extends QObject>> out,
                                  boolean insideDelegate) {
        String id = idOf(obj);
        Class<? extends QObject> selfType = registry.resolve(obj.typeName);
        if (id != null && !insideDelegate) {
            if (out.put(id, selfType) != null) {
                throw new IllegalArgumentException("duplicate id: " + id);
            }
        }
        boolean childIsDelegate = DelegateHost.class.isAssignableFrom(selfType);
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.ChildObject) {
                collectIds(((Ast.ChildObject) m).object, registry, out,
                           insideDelegate || childIsDelegate);
            } else if (m instanceof Ast.PropertyBinding) {
                Ast.Value v = ((Ast.PropertyBinding) m).value;
                if (v instanceof Ast.ObjectValue) {
                    collectIds(((Ast.ObjectValue) v).object, registry, out, insideDelegate);
                } else if (v instanceof Ast.ObjectListValue) {
                    for (Ast.ObjectNode n : ((Ast.ObjectListValue) v).objects) {
                        collectIds(n, registry, out, insideDelegate);
                    }
                }
            }
        }
    }
}
