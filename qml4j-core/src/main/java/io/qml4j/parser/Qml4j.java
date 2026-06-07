package io.qml4j.parser;

import io.qml4j.parser.ast.Ast;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class Qml4j {

    private Qml4j() {}

    public static Ast.QmlDocument parse(String source) {
        // Upstream .qml files often carry a UTF-8 BOM; strip it so the lexer starts clean.
        if (!source.isEmpty() && source.charAt(0) == '﻿') {
            source = source.substring(1);
        }
        return parse(CharStreams.fromString(source));
    }

    public static Ast.QmlDocument parse(CharStream input) {
        QmlLexer lexer = new QmlLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(THROWING);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        QmlParser parser = new QmlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(THROWING);

        QmlParser.QmlDocumentContext tree = parser.qmlDocument();
        return new AstBuilder().visitQmlDocument(tree);
    }

    private static final BaseErrorListener THROWING = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new QmlSyntaxException(line, charPositionInLine, msg);
        }
    };

    public static final class QmlSyntaxException extends RuntimeException {
        public final int line;
        public final int column;
        public QmlSyntaxException(int line, int column, String msg) {
            super("line " + line + ":" + column + " " + msg);
            this.line = line;
            this.column = column;
        }
    }
}
