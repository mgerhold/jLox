package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/*
program        → declaration* EOF ;
declaration    → varDecl
               | statement ;
varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
statement      → exprStmt
               | printStmt
               | block ;
exprStmt       → expression ";" ;
printStmt      → "print" expression ";" ;
block          → "{" declaration* "}" ;
expression     → assignment ;
assignment     → IDENTIFIER "=" assignment
               | sequence ;
sequence       → conditional ( "," conditional)* ;
conditional    → equality ( "?" expression ":" conditional )? ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | primary ;
primary        → "true" | "false" | "nil"
               | NUMBER | STRING
               | "(" expression ")"
               | IDENTIFIER ;
 */

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private boolean allowExpression;
    private boolean foundExpression = false;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    Object parseRepl() {
        allowExpression = true;
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
            if (foundExpression) {
                var last = statements.get(statements.size() - 1);
                return ((Stmt.Expression) last).expression;
            }
            allowExpression = false;
        }
        return statements;
    }

    // declaration    → varDecl
    //                | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return variableDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
    private Stmt variableDeclaration() {
        final var identifier = consume(IDENTIFIER, "Identifier expected after 'var'.");
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(identifier, initializer);
    }

    // statement      → exprStmt
    //                | printStmt
    //                | block ;
    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        return expressionStatement();
    }

    // printStmt      → "print" expression ";" ;
    private Stmt printStatement() {
        final var argument = expression();
        consume(SEMICOLON, "Expected ';' after expression.");
        return new Stmt.Print(argument);
    }

    // exprStmt       → expression ";" ;
    private Stmt expressionStatement() {
        final var expr = expression();
        if (allowExpression && isAtEnd()) {
            foundExpression = true;
        } else {
            consume(SEMICOLON, "Expected ';' after expression.");
        }
        return new Stmt.Expression(expr);
    }

    // block          → "{" declaration* "}" ;
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd() && !check(RIGHT_BRACE)) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expected '}' to close block.");
        return statements;
    }

    // expression     → assignment ;
    private Expr expression() {
        return assignment();
    }

    // assignment     → IDENTIFIER "=" assignment
    //                | sequence ;
    private Expr assignment() {
        final var expr = sequence();
        if (match(EQUAL)) {
            final var lastTokenBeforeEquals = previous();
            final var value = assignment();

            if (expr instanceof Expr.Variable) {
                final var name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            // the result of the call to error(), which is an exception, is purposely not thrown
            // because the parser should not enter "panic mode" here
            error(lastTokenBeforeEquals, "l-value is required left of an assignment.");
        }

        return expr;
    }

    // sequence       → conditional ( "," conditional)* ;
    private Expr sequence() {
        Expr expr = conditional();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = conditional();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // conditional    → equality ( "?" expression ":" conditional )? ;
    private Expr conditional() {
        Expr expr = equality();
        if (match(QUESTION_MARK)) {
            Expr thenBranch = expression();
            consume(COLON, "Expected ':' after then-branch of conditional expression.");
            Expr elseBranch = conditional();
            expr = new Expr.Conditional(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term           → factor ( ( "-" | "+" ) factor )* ;
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor         → unary ( ( "/" | "*" ) unary )* ;
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary          → ( "!" | "-" ) unary
    //               | primary ;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            return new Expr.Unary(previous(), unary());
        }
        return primary();
    }

    // primary        → "true" | "false" | "nil"
    //                | NUMBER | STRING
    //                | "(" expression ")"
    //                | IDENTIFIER ;
    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NIL)) {
            return new Expr.Literal(null);
        }
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(LEFT_PAREN)) {
            var expr = expression();
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(expr);
        }
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        throw error(peek(), "Expected expression.");
    }


    private boolean match(TokenType... types) {
        for (var type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            ++current;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
