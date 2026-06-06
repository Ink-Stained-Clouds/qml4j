package io.qml4j.compiler.bytecode;

import io.qml4j.parser.ast.Ast;

// Emits the bytecode for one kind of object member. QmlCompiler keys a strategy
// per Ast.ObjectMember subtype, replacing the instanceof dispatch in emitMember.
@FunctionalInterface
interface MemberEmitter {
    void emit(Ast.ObjectMember m, EmitContext ctx);
}
