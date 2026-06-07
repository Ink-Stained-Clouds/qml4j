grammar Qml;

// =====================
// Parser rules
// =====================

qmlDocument
    : (importDeclaration | pragmaDeclaration)* rootObject EOF
    ;

importDeclaration
    : 'import' (StringLiteral | qualifiedId) version? ('as' Identifier)? ';'?
    ;

pragmaDeclaration
    : 'pragma' Identifier ';'?
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
    : 'function' Identifier '(' (Identifier ('=' assignmentExpr)? (',' Identifier ('=' assignmentExpr)?)*)? ')' '{' statement* '}'
    ;

behaviorDeclaration
    : qualifiedId 'on' Identifier ('.' Identifier)* '{' objectMember* '}'
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
    | ifStatement
    | forInStatement
    | forStatement
    | whileStatement
    | expression
    ;

statementBlock
    : '{' statement* '}'
    ;

statement
    : statementBlock
    | varStatement
    | ifStatement
    | whileStatement
    | forInStatement
    | forStatement
    | switchStatement
    | breakStatement
    | continueStatement
    | returnStatement
    | expressionStatement
    ;

switchStatement
    : 'switch' '(' expression ')' '{' switchClause* '}'
    ;

switchClause
    : 'case' expression ':' statement*
    | 'default' ':' statement*
    ;

whileStatement
    : 'while' '(' expression ')' statement
    ;

forStatement
    : 'for' '(' forInit? ';' expression? ';' expression? ')' statement
    ;

forInStatement
    : 'for' '(' ('var' | 'let' | 'const')? Identifier 'in' expression ')' statement
    ;

forInit
    : ('var' | 'let' | 'const') Identifier ('=' expression)?
    | expression
    ;

breakStatement
    : 'break' ';'?
    ;

continueStatement
    : 'continue' ';'?
    ;

returnStatement
    : 'return' expression? ';'?
    ;

varStatement
    : ('var' | 'let' | 'const') Identifier ('=' expression)? (',' Identifier ('=' expression)?)* ';'?
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
    | 'property'
    ;

// JS expression subset
expression
    : assignmentExpr
    ;

assignmentExpr
    : arrowFunction
    | condExpr (assignOp assignmentExpr)?
    ;

assignOp
    : '=' | '+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '|=' | '^='
    ;

arrowFunction
    : Identifier '=>' arrowBody
    | '(' (Identifier ('=' assignmentExpr)? (',' Identifier ('=' assignmentExpr)?)*)? ')' '=>' arrowBody
    ;

arrowBody
    : '{' statement* '}'
    | assignmentExpr
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
    | incDecOp unaryExpr
    | postfixExpr
    ;

unaryOp
    : '-' | '+' | '!' | '~' | 'typeof' | 'void' | 'delete'
    ;

postfixExpr
    : primaryExpr postfixSuffix* incDecOp?
    ;

incDecOp
    : '++' | '--'
    ;

postfixSuffix
    : '.' Identifier                                 # memberAccess
    | '(' (spreadOrExpr (',' spreadOrExpr)*)? ')'    # call
    | '[' expression ']'                             # indexAccess
    ;

spreadOrExpr
    : '...' expression
    | expression
    ;

primaryExpr
    : literal
    | arrayLiteral
    | objectLiteral
    | functionExpr
    | newExpr
    | TemplateLiteral
    | Identifier
    | '(' expression ')'
    ;

functionExpr
    : 'function' name=Identifier? '(' (params+=Identifier ('=' assignmentExpr)? (',' params+=Identifier ('=' assignmentExpr)?)*)? ')' '{' statement* '}'
    ;

newExpr
    : 'new' qualifiedId ('(' (spreadOrExpr (',' spreadOrExpr)*)? ')')?
    ;

arrayLiteral
    : '[' (spreadOrExpr (',' spreadOrExpr)*)? ','? ']'
    ;

objectLiteral
    : '{' (objectLiteralEntry (',' objectLiteralEntry)*)? ','? '}'
    ;

objectLiteralEntry
    : (Identifier | StringLiteral) ':' expression
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
    : '0' [xX] [0-9a-fA-F]+
    | Digits
    ;

fragment Digits
    : [0-9]+
    ;

StringLiteral
    : '"' ( ~["\\] | EscapeSeq )* '"'
    | '\'' ( ~['\\] | EscapeSeq )* '\''
    ;

TemplateLiteral
    : '`' ( ~[`\\] | EscapeSeq )* '`'
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
