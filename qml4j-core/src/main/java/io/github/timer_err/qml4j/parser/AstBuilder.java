package io.github.timer_err.qml4j.parser;

import io.github.timer_err.qml4j.parser.ast.Ast;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

final class AstBuilder extends QmlBaseVisitor<Object> {

    @Override
    public Ast.QmlDocument visitQmlDocument(QmlParser.QmlDocumentContext ctx) {
        List<Ast.ImportNode> imports = new ArrayList<>();
        for (QmlParser.ImportDeclarationContext ic : ctx.importDeclaration()) {
            imports.add(visitImportDeclaration(ic));
        }
        List<String> pragmas = new ArrayList<>();
        for (QmlParser.PragmaDeclarationContext pc : ctx.pragmaDeclaration()) {
            pragmas.add(pc.Identifier().getText());
        }
        Ast.ObjectNode root = (Ast.ObjectNode) visit(ctx.rootObject().objectDeclaration());
        return new Ast.QmlDocument(imports, pragmas, root);
    }

    @Override
    public Ast.ImportNode visitImportDeclaration(QmlParser.ImportDeclarationContext ctx) {
        String moduleOrPath;
        boolean isString = false;
        if (ctx.StringLiteral() != null) {
            moduleOrPath = unquote(ctx.StringLiteral().getText());
            isString = true;
        } else {
            moduleOrPath = ctx.qualifiedId().getText();
        }
        String version = ctx.version() != null ? ctx.version().getText() : null;
        String alias = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        return new Ast.ImportNode(isString, moduleOrPath, version, alias);
    }

    @Override
    public Ast.ObjectNode visitObjectDeclaration(QmlParser.ObjectDeclarationContext ctx) {
        String type = ctx.qualifiedId().getText();
        List<Ast.ObjectMember> members = new ArrayList<>();
        for (QmlParser.ObjectMemberContext mc : ctx.objectMember()) {
            members.add((Ast.ObjectMember) visit(mc));
        }
        return new Ast.ObjectNode(type, members);
    }

    @Override
    public Ast.ObjectMember visitObjectMember(QmlParser.ObjectMemberContext ctx) {
        if (ctx.propertyDeclaration() != null) return (Ast.ObjectMember) visit(ctx.propertyDeclaration());
        if (ctx.signalDeclaration() != null) return (Ast.ObjectMember) visit(ctx.signalDeclaration());
        if (ctx.functionDeclaration() != null) return (Ast.ObjectMember) visit(ctx.functionDeclaration());
        if (ctx.behaviorDeclaration() != null) return (Ast.ObjectMember) visit(ctx.behaviorDeclaration());
        if (ctx.inlineComponentDeclaration() != null) {
            return (Ast.ObjectMember) visit(ctx.inlineComponentDeclaration());
        }
        if (ctx.propertyBinding() != null) return (Ast.ObjectMember) visit(ctx.propertyBinding());
        Ast.ObjectNode child = (Ast.ObjectNode) visit(ctx.objectDeclaration());
        return new Ast.ChildObject(child);
    }

    @Override
    public Ast.ObjectMember visitInlineComponentDeclaration(
            QmlParser.InlineComponentDeclarationContext ctx) {
        Ast.ObjectNode body = (Ast.ObjectNode) visit(ctx.objectDeclaration());
        // Identifier(0) is the soft keyword "component"; Identifier(1) is the type name.
        return new Ast.InlineComponent(ctx.Identifier(1).getText(), body);
    }

    @Override
    public Ast.FunctionDeclaration visitFunctionDeclaration(QmlParser.FunctionDeclarationContext ctx) {
        String name = ctx.Identifier(0).getText();
        List<String> params = new ArrayList<>();
        for (int i = 1; i < ctx.Identifier().size(); i++) {
            params.add(ctx.Identifier(i).getText());
        }
        List<Ast.Statement> stmts = new ArrayList<>();
        for (QmlParser.StatementContext sc : ctx.statement()) {
            stmts.add(visitStatement(sc));
        }
        return new Ast.FunctionDeclaration(name, params, new Ast.Block(stmts), bracedBodySource(ctx));
    }

    @Override
    public Ast.BehaviorMember visitBehaviorDeclaration(QmlParser.BehaviorDeclarationContext ctx) {
        String type = ctx.qualifiedId().getText();
        StringBuilder prop = new StringBuilder();
        for (org.antlr.v4.runtime.tree.TerminalNode id : ctx.Identifier()) {
            if (prop.length() > 0) prop.append('.');
            prop.append(id.getText());
        }
        List<Ast.ObjectMember> members = new ArrayList<>();
        for (QmlParser.ObjectMemberContext mc : ctx.objectMember()) {
            members.add((Ast.ObjectMember) visit(mc));
        }
        return new Ast.BehaviorMember(type, prop.toString(), members);
    }

    @Override
    public Ast.SignalDeclaration visitSignalDeclaration(QmlParser.SignalDeclarationContext ctx) {
        String name = ctx.Identifier().getText();
        List<String> params = new ArrayList<>();
        for (QmlParser.SignalParamContext pc : ctx.signalParam()) {
            params.add(pc.Identifier().getText());
        }
        return new Ast.SignalDeclaration(name, params);
    }

    @Override
    public Ast.PropertyDeclaration visitPropertyDeclaration(QmlParser.PropertyDeclarationContext ctx) {
        boolean isDefault = false, isRequired = false, isReadonly = false;
        for (QmlParser.ModifierContext mc : ctx.modifier()) {
            String t = mc.getText();
            if ("default".equals(t)) isDefault = true;
            else if ("required".equals(t)) isRequired = true;
            else if ("readonly".equals(t)) isReadonly = true;
        }
        String type = ctx.typeName().getText();
        String name = ctx.Identifier().getText();
        Ast.Value init = ctx.value() != null ? (Ast.Value) visit(ctx.value()) : null;
        return new Ast.PropertyDeclaration(isDefault, isRequired, isReadonly, type, name, init);
    }

    @Override
    public Ast.PropertyBinding visitPropertyBinding(QmlParser.PropertyBindingContext ctx) {
        List<String> path = new ArrayList<>();
        for (QmlParser.IdLikeContext id : ctx.qualifiedId().idLike()) path.add(id.getText());
        Ast.Value v = (Ast.Value) visit(ctx.value());
        return new Ast.PropertyBinding(path, v);
    }

    @Override
    public Ast.Value visitValue(QmlParser.ValueContext ctx) {
        if (ctx.objectDeclaration() != null && ctx.objectDeclaration().size() == 1 && ctx.getChildCount() == 1) {
            return new Ast.ObjectValue((Ast.ObjectNode) visit(ctx.objectDeclaration(0)));
        }
        if (!ctx.objectDeclaration().isEmpty()) {
            List<Ast.ObjectNode> objs = new ArrayList<>();
            for (QmlParser.ObjectDeclarationContext oc : ctx.objectDeclaration()) {
                objs.add((Ast.ObjectNode) visit(oc));
            }
            return new Ast.ObjectListValue(objs);
        }
        if (ctx.statementBlock() != null) {
            return new Ast.StatementBlockValue(visitStatementBlock(ctx.statementBlock()), rawSource(ctx.statementBlock()));
        }
        // A bare control-flow statement as a handler body (onExited: if (...) ...).
        if (ctx.ifStatement() != null) {
            return new Ast.StatementBlockValue(oneStatement(visitIfStatement(ctx.ifStatement())), rawSource(ctx.ifStatement()));
        }
        if (ctx.forInStatement() != null) {
            return new Ast.StatementBlockValue(oneStatement(visitForInStatement(ctx.forInStatement())), rawSource(ctx.forInStatement()));
        }
        if (ctx.forStatement() != null) {
            return new Ast.StatementBlockValue(oneStatement(visitForStatement(ctx.forStatement())), rawSource(ctx.forStatement()));
        }
        if (ctx.whileStatement() != null) {
            return new Ast.StatementBlockValue(oneStatement(visitWhileStatement(ctx.whileStatement())), rawSource(ctx.whileStatement()));
        }
        QmlParser.ExpressionContext ec = ctx.expression();
        return new Ast.ExpressionValue((Ast.Expression) visit(ec), rawSource(ec));
    }

    // The raw `{ ... }` body of a function declaration (the braces have no sub-rule,
    // so capture from the opening brace token to the closing one). Fed to the Rhino
    // backend; null if unavailable, which keeps the function on the ASM backend.
    private static String bracedBodySource(QmlParser.FunctionDeclarationContext ctx) {
        Token open = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree ch = ctx.getChild(i);
            if (ch instanceof TerminalNode && "{".equals(ch.getText())) {
                open = ((TerminalNode) ch).getSymbol();
                break;
            }
        }
        Token close = ctx.getStop();
        if (open == null || close == null) return null;
        CharStream cs = open.getInputStream();
        if (cs == null || open.getStartIndex() < 0 || close.getStopIndex() < 0) return null;
        return cs.getText(Interval.of(open.getStartIndex(), close.getStopIndex()));
    }

    // The original source substring of a rule (whitespace preserved), via the
    // CharStream the tokens came from. Null if unavailable (e.g. synthesized nodes).
    private static String rawSource(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        if (start == null || stop == null) return null;
        CharStream cs = start.getInputStream();
        if (cs == null || start.getStartIndex() < 0 || stop.getStopIndex() < 0) return null;
        return cs.getText(Interval.of(start.getStartIndex(), stop.getStopIndex()));
    }

    private static Ast.Block oneStatement(Ast.Statement s) {
        List<Ast.Statement> stmts = new ArrayList<>();
        stmts.add(s);
        return new Ast.Block(stmts);
    }

    @Override
    public Ast.SwitchStmt visitSwitchStatement(QmlParser.SwitchStatementContext ctx) {
        Ast.Expression disc = (Ast.Expression) visit(ctx.expression());
        List<Ast.SwitchClause> clauses = new ArrayList<>();
        for (QmlParser.SwitchClauseContext cc : ctx.switchClause()) {
            Ast.Expression label = cc.expression() != null ? (Ast.Expression) visit(cc.expression()) : null;
            List<Ast.Statement> body = new ArrayList<>();
            for (QmlParser.StatementContext sc : cc.statement()) body.add(visitStatement(sc));
            clauses.add(new Ast.SwitchClause(label, body));
        }
        return new Ast.SwitchStmt(disc, clauses);
    }

    @Override
    public Ast.Block visitStatementBlock(QmlParser.StatementBlockContext ctx) {
        List<Ast.Statement> stmts = new ArrayList<>();
        for (QmlParser.StatementContext sc : ctx.statement()) {
            stmts.add(visitStatement(sc));
        }
        return new Ast.Block(stmts);
    }

    @Override
    public Ast.Statement visitStatement(QmlParser.StatementContext ctx) {
        if (ctx.statementBlock() != null) return visitStatementBlock(ctx.statementBlock());
        if (ctx.varStatement() != null) return visitVarStatement(ctx.varStatement());
        if (ctx.ifStatement() != null) return visitIfStatement(ctx.ifStatement());
        if (ctx.whileStatement() != null) return visitWhileStatement(ctx.whileStatement());
        if (ctx.forInStatement() != null) return visitForInStatement(ctx.forInStatement());
        if (ctx.forStatement() != null) return visitForStatement(ctx.forStatement());
        if (ctx.switchStatement() != null) return visitSwitchStatement(ctx.switchStatement());
        if (ctx.breakStatement() != null) return new Ast.BreakStmt();
        if (ctx.continueStatement() != null) return new Ast.ContinueStmt();
        if (ctx.returnStatement() != null) return visitReturnStatement(ctx.returnStatement());
        return visitExpressionStatement(ctx.expressionStatement());
    }

    @Override
    public Ast.WhileStmt visitWhileStatement(QmlParser.WhileStatementContext ctx) {
        Ast.Expression cond = (Ast.Expression) visit(ctx.expression());
        Ast.Statement body = visitStatement(ctx.statement());
        return new Ast.WhileStmt(cond, body);
    }

    @Override
    public Ast.ForStmt visitForStatement(QmlParser.ForStatementContext ctx) {
        Ast.Statement init = ctx.forInit() != null ? visitForInit(ctx.forInit()) : null;
        Ast.Expression cond = ctx.expression().size() > 0
            ? (Ast.Expression) visit(ctx.expression(0)) : null;
        Ast.Expression update = ctx.expression().size() > 1
            ? (Ast.Expression) visit(ctx.expression(1)) : null;
        Ast.Statement body = visitStatement(ctx.statement());
        return new Ast.ForStmt(init, cond, update, body);
    }

    @Override
    public Ast.ForInStmt visitForInStatement(QmlParser.ForInStatementContext ctx) {
        String name = ctx.Identifier().getText();
        Ast.Expression iterable = (Ast.Expression) visit(ctx.expression());
        Ast.Statement body = visitStatement(ctx.statement());
        return new Ast.ForInStmt(name, iterable, body);
    }

    @Override
    public Ast.Statement visitForInit(QmlParser.ForInitContext ctx) {
        if (ctx.Identifier() != null) {
            String name = ctx.Identifier().getText();
            Ast.Expression init = ctx.expression() != null
                ? (Ast.Expression) visit(ctx.expression()) : null;
            return new Ast.VarDecl(name, init);
        }
        return new Ast.ExprStmt((Ast.Expression) visit(ctx.expression()));
    }

    @Override
    public Ast.ReturnStmt visitReturnStatement(QmlParser.ReturnStatementContext ctx) {
        Ast.Expression v = ctx.expression() != null
            ? (Ast.Expression) visit(ctx.expression())
            : null;
        return new Ast.ReturnStmt(v);
    }

    @Override
    public Ast.Statement visitVarStatement(QmlParser.VarStatementContext ctx) {
        List<TerminalNode> ids = ctx.Identifier();
        if (ids.size() == 1) {
            Ast.Expression init = ctx.expression().isEmpty()
                ? null : (Ast.Expression) visit(ctx.expression(0));
            return new Ast.VarDecl(ids.get(0).getText(), init);
        }
        // `var a, b = 1, c`: the inits aren't paired to names in the AST (vestigial --
        // execution uses the captured raw source via Rhino); bind the names.
        List<Ast.Statement> decls = new ArrayList<>();
        for (TerminalNode id : ids) decls.add(new Ast.VarDecl(id.getText(), null));
        return new Ast.Block(decls);
    }

    @Override
    public Ast.IfStmt visitIfStatement(QmlParser.IfStatementContext ctx) {
        Ast.Expression cond = (Ast.Expression) visit(ctx.expression());
        Ast.Statement thenBranch = visitStatement(ctx.statement(0));
        Ast.Statement elseBranch = ctx.statement().size() > 1
            ? visitStatement(ctx.statement(1))
            : null;
        return new Ast.IfStmt(cond, thenBranch, elseBranch);
    }

    @Override
    public Ast.ExprStmt visitExpressionStatement(QmlParser.ExpressionStatementContext ctx) {
        return new Ast.ExprStmt((Ast.Expression) visit(ctx.expression()));
    }

    // ---- Expressions ----

    @Override
    public Ast.Expression visitExpression(QmlParser.ExpressionContext ctx) {
        return (Ast.Expression) visit(ctx.assignmentExpr());
    }

    @Override
    public Ast.Expression visitAssignmentExpr(QmlParser.AssignmentExprContext ctx) {
        if (ctx.arrowFunction() != null) {
            return visitArrowFunction(ctx.arrowFunction());
        }
        Ast.Expression left = (Ast.Expression) visit(ctx.condExpr());
        if (ctx.assignmentExpr() == null) return left;
        Ast.Expression right = (Ast.Expression) visit(ctx.assignmentExpr());
        String op = ctx.assignOp().getText();
        if ("=".equals(op)) return new Ast.AssignmentExpr(left, right);
        // `a += b` desugars to `a = a + b` for the AST (free-id/singleton walks); the
        // captured raw source keeps the compound form for Rhino to execute verbatim.
        String bin = op.substring(0, op.length() - 1);
        return new Ast.AssignmentExpr(left, new Ast.BinaryExpr(bin, left, right));
    }

    @Override
    public Ast.ArrowFunctionExpr visitArrowFunction(QmlParser.ArrowFunctionContext ctx) {
        List<String> params = new ArrayList<>();
        for (org.antlr.v4.runtime.tree.TerminalNode id : ctx.Identifier()) {
            params.add(id.getText());
        }
        QmlParser.ArrowBodyContext body = ctx.arrowBody();
        String src = rawSource(body);
        if (body.assignmentExpr() != null) {
            Ast.Expression expr = (Ast.Expression) visit(body.assignmentExpr());
            return new Ast.ArrowFunctionExpr(params, expr, null, src);
        }
        List<Ast.Statement> stmts = new ArrayList<>();
        for (QmlParser.StatementContext sc : body.statement()) {
            stmts.add(visitStatement(sc));
        }
        return new Ast.ArrowFunctionExpr(params, null, new Ast.Block(stmts), src);
    }

    @Override
    public Ast.Expression visitCondExpr(QmlParser.CondExprContext ctx) {
        Ast.Expression cond = (Ast.Expression) visit(ctx.logicalOrExpr());
        if (ctx.expression().isEmpty()) return cond;
        Ast.Expression thenE = (Ast.Expression) visit(ctx.expression(0));
        Ast.Expression elseE = (Ast.Expression) visit(ctx.expression(1));
        return new Ast.CondExpr(cond, thenE, elseE);
    }

    @Override
    public Ast.Expression visitLogicalOrExpr(QmlParser.LogicalOrExprContext ctx) {
        return leftAssoc(ctx.logicalAndExpr(), "||");
    }

    @Override
    public Ast.Expression visitLogicalAndExpr(QmlParser.LogicalAndExprContext ctx) {
        return leftAssoc(ctx.bitwiseOrExpr(), "&&");
    }

    @Override
    public Ast.Expression visitBitwiseOrExpr(QmlParser.BitwiseOrExprContext ctx) {
        return leftAssoc(ctx.bitwiseXorExpr(), "|");
    }

    @Override
    public Ast.Expression visitBitwiseXorExpr(QmlParser.BitwiseXorExprContext ctx) {
        return leftAssoc(ctx.bitwiseAndExpr(), "^");
    }

    @Override
    public Ast.Expression visitBitwiseAndExpr(QmlParser.BitwiseAndExprContext ctx) {
        return leftAssoc(ctx.equalityExpr(), "&");
    }

    @Override
    public Ast.Expression visitEqualityExpr(QmlParser.EqualityExprContext ctx) {
        Ast.Expression left = (Ast.Expression) visit(ctx.relationalExpr(0));
        for (int i = 0; i < ctx.equalityOp().size(); i++) {
            String op = ctx.equalityOp(i).getText();
            Ast.Expression right = (Ast.Expression) visit(ctx.relationalExpr(i + 1));
            left = new Ast.BinaryExpr(op, left, right);
        }
        return left;
    }

    @Override
    public Ast.Expression visitRelationalExpr(QmlParser.RelationalExprContext ctx) {
        Ast.Expression left = (Ast.Expression) visit(ctx.additiveExpr(0));
        for (int i = 0; i < ctx.relationalOp().size(); i++) {
            String op = ctx.relationalOp(i).getText();
            Ast.Expression right = (Ast.Expression) visit(ctx.additiveExpr(i + 1));
            left = new Ast.BinaryExpr(op, left, right);
        }
        return left;
    }

    @Override
    public Ast.Expression visitAdditiveExpr(QmlParser.AdditiveExprContext ctx) {
        Ast.Expression left = (Ast.Expression) visit(ctx.multiplicativeExpr(0));
        for (int i = 0; i < ctx.additiveOp().size(); i++) {
            String op = ctx.additiveOp(i).getText();
            Ast.Expression right = (Ast.Expression) visit(ctx.multiplicativeExpr(i + 1));
            left = new Ast.BinaryExpr(op, left, right);
        }
        return left;
    }

    @Override
    public Ast.Expression visitMultiplicativeExpr(QmlParser.MultiplicativeExprContext ctx) {
        Ast.Expression left = (Ast.Expression) visit(ctx.unaryExpr(0));
        for (int i = 0; i < ctx.multiplicativeOp().size(); i++) {
            String op = ctx.multiplicativeOp(i).getText();
            Ast.Expression right = (Ast.Expression) visit(ctx.unaryExpr(i + 1));
            left = new Ast.BinaryExpr(op, left, right);
        }
        return left;
    }

    @Override
    public Ast.Expression visitUnaryExpr(QmlParser.UnaryExprContext ctx) {
        if (ctx.unaryOp() != null) {
            return new Ast.UnaryExpr(ctx.unaryOp().getText(), (Ast.Expression) visit(ctx.unaryExpr()));
        }
        if (ctx.incDecOp() != null) {
            // ++i / --i desugars to `i = i +/- 1`, like the postfix form in for-loop
            // update context. The Rhino backend executes the captured source directly.
            Ast.Expression target = (Ast.Expression) visit(ctx.unaryExpr());
            String bin = ctx.incDecOp().getText().equals("++") ? "+" : "-";
            Ast.Expression one = new Ast.LiteralExpr(Ast.LiteralKind.INT, 1L);
            return new Ast.AssignmentExpr(target, new Ast.BinaryExpr(bin, target, one));
        }
        return (Ast.Expression) visit(ctx.postfixExpr());
    }

    @Override
    public Ast.Expression visitPostfixExpr(QmlParser.PostfixExprContext ctx) {
        Ast.Expression cur = (Ast.Expression) visit(ctx.primaryExpr());
        for (QmlParser.PostfixSuffixContext sc : ctx.postfixSuffix()) {
            if (sc instanceof QmlParser.MemberAccessContext) {
                QmlParser.MemberAccessContext m = (QmlParser.MemberAccessContext) sc;
                cur = new Ast.MemberExpr(cur, m.Identifier().getText());
            } else if (sc instanceof QmlParser.IndexAccessContext) {
                QmlParser.IndexAccessContext ix = (QmlParser.IndexAccessContext) sc;
                cur = new Ast.IndexExpr(cur, (Ast.Expression) visit(ix.expression()));
            } else {
                QmlParser.CallContext c = (QmlParser.CallContext) sc;
                List<Ast.Expression> args = new ArrayList<>();
                for (QmlParser.SpreadOrExprContext se : c.spreadOrExpr()) {
                    args.add(visitSpreadOrExpr(se));
                }
                cur = new Ast.CallExpr(cur, args);
            }
        }
        if (ctx.incDecOp() != null) {
            // i++ / i-- desugars to `i = i +/- 1`. Statement-context semantics (the
            // common for-loop update); the postfix old-value result isn't preserved.
            String bin = ctx.incDecOp().getText().equals("++") ? "+" : "-";
            Ast.Expression one = new Ast.LiteralExpr(Ast.LiteralKind.INT, 1L);
            cur = new Ast.AssignmentExpr(cur, new Ast.BinaryExpr(bin, cur, one));
        }
        return cur;
    }

    @Override
    public Ast.Expression visitPrimaryExpr(QmlParser.PrimaryExprContext ctx) {
        if (ctx.literal() != null) return (Ast.Expression) visit(ctx.literal());
        if (ctx.arrayLiteral() != null) return visitArrayLiteral(ctx.arrayLiteral());
        if (ctx.objectLiteral() != null) return visitObjectLiteral(ctx.objectLiteral());
        if (ctx.functionExpr() != null) return visitFunctionExpr(ctx.functionExpr());
        if (ctx.newExpr() != null) return visitNewExpr(ctx.newExpr());
        if (ctx.TemplateLiteral() != null) return parseTemplateLiteral(ctx.TemplateLiteral().getText());
        if (ctx.Identifier() != null) return new Ast.IdentifierExpr(ctx.Identifier().getText());
        return (Ast.Expression) visit(ctx.expression());
    }

    // A function expression reuses ArrowFunctionExpr: params bind in the free-identifier
    // walk and the body is captured as raw source for Rhino. The optional name is dropped
    // from the AST (the raw source keeps it).
    @Override
    public Ast.Expression visitFunctionExpr(QmlParser.FunctionExprContext ctx) {
        List<String> params = new ArrayList<>();
        for (Token p : ctx.params) params.add(p.getText());
        List<Ast.Statement> stmts = new ArrayList<>();
        for (QmlParser.StatementContext sc : ctx.statement()) stmts.add(visitStatement(sc));
        return new Ast.ArrowFunctionExpr(params, null, new Ast.Block(stmts), rawSource(ctx));
    }

    // `new Foo(args)` reuses CallExpr: the constructor name walks as a free identifier
    // (resolved against JS_GLOBALS / singletons), the args walk normally, and the raw
    // source keeps the `new` for Rhino.
    @Override
    public Ast.Expression visitNewExpr(QmlParser.NewExprContext ctx) {
        Ast.Expression callee = qualifiedIdToExpr(ctx.qualifiedId());
        List<Ast.Expression> args = new ArrayList<>();
        for (QmlParser.SpreadOrExprContext se : ctx.spreadOrExpr()) args.add(visitSpreadOrExpr(se));
        return new Ast.CallExpr(callee, args);
    }

    private Ast.Expression qualifiedIdToExpr(QmlParser.QualifiedIdContext ctx) {
        List<QmlParser.IdLikeContext> parts = ctx.idLike();
        Ast.Expression e = new Ast.IdentifierExpr(parts.get(0).getText());
        for (int i = 1; i < parts.size(); i++) e = new Ast.MemberExpr(e, parts.get(i).getText());
        return e;
    }

    @Override
    public Ast.Expression visitArrayLiteral(QmlParser.ArrayLiteralContext ctx) {
        List<Ast.Expression> elems = new ArrayList<>();
        for (QmlParser.SpreadOrExprContext se : ctx.spreadOrExpr()) {
            elems.add(visitSpreadOrExpr(se));
        }
        return new Ast.ArrayLitExpr(elems);
    }

    @Override
    public Ast.Expression visitSpreadOrExpr(QmlParser.SpreadOrExprContext ctx) {
        Ast.Expression inner = (Ast.Expression) visit(ctx.expression());
        boolean isSpread = ctx.getChildCount() > 1; // '...' expression
        return isSpread ? new Ast.SpreadExpr(inner) : inner;
    }

    @Override
    public Ast.Expression visitObjectLiteral(QmlParser.ObjectLiteralContext ctx) {
        List<String> keys = new ArrayList<>();
        List<Ast.Expression> values = new ArrayList<>();
        for (QmlParser.ObjectLiteralEntryContext entry : ctx.objectLiteralEntry()) {
            String key;
            if (entry.Identifier() != null) {
                key = entry.Identifier().getText();
            } else {
                key = unquote(entry.StringLiteral().getText());
            }
            keys.add(key);
            values.add((Ast.Expression) visit(entry.expression()));
        }
        return new Ast.ObjectLitExpr(keys, values);
    }

    @Override
    public Ast.Expression visitLiteral(QmlParser.LiteralContext ctx) {
        if (ctx.IntegerLiteral() != null) {
            // Long.decode handles 0x hex as well as decimal.
            return new Ast.LiteralExpr(Ast.LiteralKind.INT, Long.decode(ctx.IntegerLiteral().getText()));
        }
        if (ctx.FloatLiteral() != null) {
            return new Ast.LiteralExpr(Ast.LiteralKind.FLOAT, Double.parseDouble(ctx.FloatLiteral().getText()));
        }
        if (ctx.StringLiteral() != null) {
            return new Ast.LiteralExpr(Ast.LiteralKind.STRING, unquote(ctx.StringLiteral().getText()));
        }
        String t = ctx.getText();
        switch (t) {
            case "true": return new Ast.LiteralExpr(Ast.LiteralKind.BOOL, Boolean.TRUE);
            case "false": return new Ast.LiteralExpr(Ast.LiteralKind.BOOL, Boolean.FALSE);
            case "null": return new Ast.LiteralExpr(Ast.LiteralKind.NULL, null);
            case "undefined": return new Ast.LiteralExpr(Ast.LiteralKind.UNDEFINED, null);
            default: throw new IllegalStateException("unknown literal: " + t);
        }
    }

    private Ast.Expression leftAssoc(List<? extends org.antlr.v4.runtime.ParserRuleContext> operands, String op) {
        Ast.Expression left = (Ast.Expression) visit(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            left = new Ast.BinaryExpr(op, left, (Ast.Expression) visit(operands.get(i)));
        }
        return left;
    }

    private Ast.TemplateLiteralExpr parseTemplateLiteral(String raw) {
        String body = raw.substring(1, raw.length() - 1);
        List<String> parts = new ArrayList<>();
        List<Ast.Expression> exprs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char n = body.charAt(i + 1);
                switch (n) {
                    case 'n': cur.append('\n'); break;
                    case 't': cur.append('\t'); break;
                    case 'r': cur.append('\r'); break;
                    case '\\': cur.append('\\'); break;
                    case '`': cur.append('`'); break;
                    case '$': cur.append('$'); break;
                    case '\'': cur.append('\''); break;
                    case '"': cur.append('"'); break;
                    default: cur.append(n);
                }
                i += 2;
            } else if (c == '$' && i + 1 < body.length() && body.charAt(i + 1) == '{') {
                int depth = 1;
                int start = i + 2;
                int end = start;
                while (end < body.length()) {
                    char cc = body.charAt(end);
                    if (cc == '{') depth++;
                    else if (cc == '}') {
                        depth--;
                        if (depth == 0) break;
                    }
                    end++;
                }
                if (depth != 0) {
                    throw new IllegalArgumentException("unterminated ${...} in template literal: " + raw);
                }
                parts.add(cur.toString());
                cur.setLength(0);
                String exprText = body.substring(start, end);
                exprs.add(parseExpressionStandalone(exprText));
                i = end + 1;
            } else {
                cur.append(c);
                i++;
            }
        }
        parts.add(cur.toString());
        return new Ast.TemplateLiteralExpr(parts, exprs);
    }

    private Ast.Expression parseExpressionStandalone(String text) {
        QmlLexer lexer = new QmlLexer(CharStreams.fromString(text));
        QmlParser parser = new QmlParser(new CommonTokenStream(lexer));
        return (Ast.Expression) visit(parser.expression());
    }

    private static String unquote(String raw) {
        if (raw.length() < 2) return raw;
        char q = raw.charAt(0);
        if (q != '"' && q != '\'') return raw;
        StringBuilder sb = new StringBuilder(raw.length() - 2);
        for (int i = 1; i < raw.length() - 1; i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() - 1) {
                char n = raw.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '\'': sb.append('\''); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
