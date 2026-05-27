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
    | propertyBinding
    | objectDeclaration
    ;

propertyDeclaration
    : modifier* 'property' typeName Identifier (':' value)? ';'?
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
    | expression
    ;

qualifiedId
    : Identifier ('.' Identifier)*
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
