grammar Qml;

// =====================
// Parser rules
// =====================

qmlDocument
    : importDeclaration* rootObject EOF
    ;

importDeclaration
    : 'import' (StringLiteral | qualifiedId) version? ('as' Identifier)? ';'?
    ;

version
    : FloatLiteral
    | IntegerLiteral
    ;

rootObject
    : objectDeclaration
    ;

objectDeclaration
    : qualifiedId '{' objectMember* '}'
    ;

objectMember
    : propertyDeclaration
    | signalDeclaration
    | functionDeclaration
    | behaviorDeclaration
    | propertyBinding
    | objectDeclaration
    ;

functionDeclaration
    : 'function' Identifier '(' (Identifier (',' Identifier)*)? ')' '{' statement* '}'
    ;

behaviorDeclaration
    : qualifiedId 'on' Identifier '{' objectMember* '}'
    ;

propertyDeclaration
    : modifier* 'property' typeName Identifier (':' value)? ';'?
    ;

signalDeclaration
    : 'signal' Identifier ('(' (signalParam (',' signalParam)*)? ')')? ';'?
    ;

signalParam
    : typeName? Identifier
    ;

modifier
    : 'default'
    | 'required'
    | 'readonly'
    ;

typeName
    : qualifiedId ('<' qualifiedId '>')?
    ;

propertyBinding
    : qualifiedId ':' value ';'?
    ;

value
    : objectDeclaration
    | '[' objectDeclaration (',' objectDeclaration)* ','? ']'
    | statementBlock
    | expression
    ;

statementBlock
    : '{' statement* '}'
    ;

statement
    : statementBlock
    | varStatement
    | ifStatement
    | returnStatement
    | expressionStatement
    ;

returnStatement
    : 'return' expression? ';'?
    ;

varStatement
    : ('var' | 'let' | 'const') Identifier ('=' expression)? ';'?
    ;

ifStatement
    : 'if' '(' expression ')' statement ('else' statement)?
    ;

expressionStatement
    : expression ';'?
    ;

qualifiedId
    : idLike ('.' idLike)*
    ;

idLike
    : Identifier
    | 'var'
    | 'let'
    | 'const'
    ;

// JS expression subset
expression
    : assignmentExpr
    ;

assignmentExpr
    : condExpr ('=' assignmentExpr)?
    ;

condExpr
    : logicalOrExpr ('?' expression ':' expression)?
    ;

logicalOrExpr
    : logicalAndExpr ('||' logicalAndExpr)*
    ;

logicalAndExpr
    : bitwiseOrExpr ('&&' bitwiseOrExpr)*
    ;

bitwiseOrExpr
    : bitwiseXorExpr ('|' bitwiseXorExpr)*
    ;

bitwiseXorExpr
    : bitwiseAndExpr ('^' bitwiseAndExpr)*
    ;

bitwiseAndExpr
    : equalityExpr ('&' equalityExpr)*
    ;

equalityExpr
    : relationalExpr (equalityOp relationalExpr)*
    ;

equalityOp
    : '===' | '!==' | '==' | '!='
    ;

relationalExpr
    : additiveExpr (relationalOp additiveExpr)*
    ;

relationalOp
    : '<=' | '>=' | '<' | '>'
    ;

additiveExpr
    : multiplicativeExpr (additiveOp multiplicativeExpr)*
    ;

additiveOp
    : '+' | '-'
    ;

multiplicativeExpr
    : unaryExpr (multiplicativeOp unaryExpr)*
    ;

multiplicativeOp
    : '*' | '/' | '%'
    ;

unaryExpr
    : unaryOp unaryExpr
    | postfixExpr
    ;

unaryOp
    : '-' | '+' | '!'
    ;

postfixExpr
    : primaryExpr postfixSuffix*
    ;

postfixSuffix
    : '.' Identifier                                 # memberAccess
    | '(' (expression (',' expression)*)? ')'        # call
    ;

primaryExpr
    : literal
    | Identifier
    | '(' expression ')'
    ;

literal
    : IntegerLiteral
    | FloatLiteral
    | StringLiteral
    | 'true'
    | 'false'
    | 'null'
    | 'undefined'
    ;

// =====================
// Lexer rules
// =====================

FloatLiteral
    : Digits '.' Digits?
    | '.' Digits
    ;

IntegerLiteral
    : Digits
    ;

fragment Digits
    : [0-9]+
    ;

StringLiteral
    : '"' ( ~["\\] | EscapeSeq )* '"'
    | '\'' ( ~['\\] | EscapeSeq )* '\''
    ;

fragment EscapeSeq
    : '\\' .
    ;

Identifier
    : [a-zA-Z_$] [a-zA-Z0-9_$]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

LineComment
    : '//' ~[\r\n]* -> skip
    ;

BlockComment
    : '/*' .*? '*/' -> skip
    ;
