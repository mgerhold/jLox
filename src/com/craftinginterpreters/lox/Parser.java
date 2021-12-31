package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/*
program        → declaration* EOF ;
declaration    → funDecl,
               | varDecl
               | statement ;
funDecl        → "fun" IDENTIFIER "(" parameters? ")" block ;
parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
statement      → exprStmt
               | ifStmt
               | whileStmt
               | forStmt
               | breakStmt
               | continueStmt
               | returnStmt
               | printStmt
               | block ;
exprStmt       → expression ";" ;
ifStmt         → "if" "(" expression ")" statement ( "else" statement )? ;
whileStmt      → "while" "(" expression ")" statement ;
forStmt        → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
breakStmt      → "break" ";" ;
continueStmt   → "continue" ";" ;
returnStmt     → "return" expression? ";" ;
printStmt      → "print" expression ";" ;
block          → "{" declaration* "}" ;
expression     → assignment ;
assignment     → IDENTIFIER "=" assignment
               | sequence ;
sequence       → conditional ( "," conditional)* ;
conditional    → logical_or ( "?" expression ":" conditional )? ;
logical_or     → logical_and ( "or" logical_and )* ;
logical_and    → equality ( "and" equality )* ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | call ;
call           → primary ( "(" arguments? ")" )* ;
arguments      → conditional ("," conditional)* ;
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
    private int loopNestingLevel = 0;
    private int functionNestingLevel = 0;

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

    // declaration    → funDecl,
    //               | varDecl
    //               | statement ;
    private Stmt declaration() {
        try {
            if (match(FUN)) {
                return functionDeclaration();
            }
            if (match(VAR)) {
                return variableDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    //funDecl        → "fun" IDENTIFIER "(" parameters? ")" block ;
    private Stmt functionDeclaration() {
        final var name = consume(IDENTIFIER, "Expected identifier after 'fun'.");
        consume(LEFT_PAREN, "Expected '(' before parameter list of function declaration.");
        final var parameters = parameters();
        consume(RIGHT_PAREN, "Expected ')' after parameter list of function declaration.");
        consume(LEFT_BRACE, "Expected '{' to start function body.");
        ++functionNestingLevel;
        final var functionBody = block();
        --functionNestingLevel;
        return new Stmt.Fun(name, parameters, functionBody);
    }

    // parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
    private List<Token> parameters() {
        final var parameters = new ArrayList<Token>();
        do {
            if (check(RIGHT_PAREN)) { // allow for trailing comma
                break;
            }
            if (parameters.size() >= 255) {
                error(peek(), "Maximum number of function parameters exceeded. Maximum is 255.");
            }
            parameters.add(consume(IDENTIFIER, "Expected identifier token inside parameter list."));
        } while (match(COMMA));
        return parameters;
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
    //               | ifStmt
    //               | whileStmt
    //               | forStmt
    //               | breakStmt
    //               | continueStmt
    //               | returnStmt
    //               | printStmt
    //               | block ;
    private Stmt statement() {
        if (match(IF)) {
            return ifStmt();
        }
        if (match(WHILE)) {
            return whileStmt();
        }
        if (match(FOR)) {
            return forStmt();
        }
        if (match(BREAK)) {
            return breakStmt();
        }
        if (match(CONTINUE)) {
            return continueStmt();
        }
        if (match(RETURN)) {
            return returnStmt();
        }
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        return expressionStatement();
    }

    // ifStmt         → "if" "(" expression ")" statement ( "else" statement )? ;
    private Stmt ifStmt() {
        consume(LEFT_PAREN, "Expected '(' after if.");
        final var condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition of if-statement.");
        final var thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    // whileStmt      → "while" "(" expression ")" statement ;
    private Stmt whileStmt() {
        consume(LEFT_PAREN, "Expected '(' after while.");
        final var loopCondition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition of while-statement.");
        ++loopNestingLevel;
        final var loopBody = statement();
        --loopNestingLevel;
        return new Stmt.While(loopCondition, loopBody);
    }

    // forStmt        → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
    private Stmt forStmt() {
        consume(LEFT_PAREN, "Expected '(' after for.");
        Stmt initialization = null;
        if (match(VAR)) {
            initialization = variableDeclaration();
        } else if (!match(SEMICOLON)) {
            initialization = expressionStatement();
        }
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expected ';' after condition in for-loop.");
        Expr step = null;
        if (!check(RIGHT_PAREN)) {
            step = expression();
        }
        consume(RIGHT_PAREN, "Expected ')' before body of for-loop.");
        ++loopNestingLevel;
        final var loopBody = statement();
        --loopNestingLevel;
        final var statements = new ArrayList<Stmt>();
        if (initialization != null) {
            statements.add(initialization);
        }
        final var bodyStatements = new ArrayList<Stmt>();
        bodyStatements.add(loopBody);
        if (step != null) {
            bodyStatements.add(new Stmt.Expression(step));
        }
        statements.add(new Stmt.While(condition != null ? condition : new Expr.Literal(true), new Stmt.Block(bodyStatements)));
        return new Stmt.Block(statements);
    }

    // breakStmt      → "break" ";" ;
    private Stmt breakStmt() {
        if (loopNestingLevel == 0) {
            throw error(previous(), "'break' may only appear inside loops.");
        }
        consume(SEMICOLON, "Expected ';' after break.");
        return new Stmt.Break();
    }

    // continueStmt   → "continue" ";" ;
    private Stmt continueStmt() {
        if (loopNestingLevel == 0) {
            throw error(previous(), "'continue' may only appear inside loops.");
        }
        consume(SEMICOLON, "Expected ';' after continue.");
        return new Stmt.Continue();
    }

    // returnStmt     → "return" ";" ;
    private Stmt returnStmt() {
        if (functionNestingLevel == 0) {
            throw error(previous(), "'return' may only appear inside functions.");
        }
        Expr returnValue = null;
        if (!check(SEMICOLON)) {
            returnValue = expression();
        }
        consume(SEMICOLON, "Expected ';' after return.");
        return new Stmt.Return(returnValue);
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

    // conditional    → logical_or ( "?" expression ":" conditional )? ;
    private Expr conditional() {
        Expr expr = logicalOr();
        if (match(QUESTION_MARK)) {
            Expr thenBranch = expression();
            consume(COLON, "Expected ':' after then-branch of conditional expression.");
            Expr elseBranch = conditional();
            expr = new Expr.Conditional(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    // logical_or     → logical_and ( "or" logical_and )* ;
    private Expr logicalOr() {
        var expression = logicalAnd();
        while (match(OR)) {
             expression = new Expr.Logical(expression, previous(), logicalAnd());
        }
        return expression;
    }

    // logical_and    → equality ( "and" equality )* ;
    private Expr logicalAnd() {
        var expression = equality();
        while (match(AND)) {
            expression = new Expr.Logical(expression, previous(), equality());
        }
        return expression;
    }

    // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        var expr = comparison();

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
    //               | call ;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            return new Expr.Unary(previous(), unary());
        }
        return call();
    }

    // call           → primary ( "(" arguments? ")" )* ;
    private Expr call() {
        var expression = primary();
        while (match(LEFT_PAREN)) {
            final var arguments = (check(RIGHT_PAREN) ? Collections.<Expr>emptyList() : arguments());
            final var closingParen = consume(RIGHT_PAREN, "Expected ')' at the end of the function call argument list.");
            expression = new Expr.Call(expression, closingParen, arguments);
        }
        return expression;
        /*
        Expr expr = primary();

        while (true) {
          if (match(LEFT_PAREN)) {
            expr = finishCall(expr);
          } else {
            break;
          }
        }

        return expr;
         */
    }

    // arguments      → conditional ("," conditional)* ;
    private List<Expr> arguments() {
        final List<Expr> arguments = new ArrayList<>();
        do {
            if (check(RIGHT_PAREN)) { // allow for trailing comma
                break;
            }
            if (arguments.size() >= 255) {
                error(peek(), "Maximum number of function call arguments exceeded. Maximum is 255.");
            }
            arguments.add(conditional());
        } while(match(COMMA));
        return arguments;
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
