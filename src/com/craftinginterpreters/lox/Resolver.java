package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import static com.craftinginterpreters.lox.FunctionType.*;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private enum VariableState {
        DECLARED,
        DEFINED,
    }

    private final Interpreter interpreter;
    private final Stack<Map<String, VariableState>> scopes = new Stack<>();

    public Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    void resolve(Expr expression) {
        expression.accept(this);
    }

    private void resolve(Stmt statement) {
        statement.accept(this);
    }

    void resolve(List<Stmt> statements) {
        for (final var statement : statements) {
            resolve(statement);
        }
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(String name) {
        if (scopes.empty()) {
            return;
        }
        scopes.peek().put(name, VariableState.DECLARED);
    }

    private void define(String name) {
        if (scopes.empty()) {
            return;
        }
        assert scopes.peek().containsKey(name) && scopes.peek().get(name) == VariableState.DECLARED;
        scopes.peek().put(name, VariableState.DEFINED);
    }

    private void resolveLocal(Expr expression, Token name) {
        for (int i = scopes.size() - 1; i >= 0; --i) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                final var numScopeHops = scopes.size() - 1 - i;
                interpreter.resolve(expression, numScopeHops);
                return;
            }
        }
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (final var expression : expr.arguments) {
            resolve(expression);
        }
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.object);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitConditionalExpr(Expr.Conditional expr) {
        resolve(expr.condition);
        resolve(expr.thenBranch);
        resolve(expr.elseBranch);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.empty() && scopes.peek().get(expr.name.lexeme) == VariableState.DECLARED) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.loopCondition);
        resolve(stmt.loopBody);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (stmt.value != null) {
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name.lexeme);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name.lexeme);
        return null;
    }

    private void resolveFunction(Stmt.Fun function, FunctionType type) {
        beginScope();
        for (final var parameter : function.parameters) {
            declare(parameter.lexeme);
            define(parameter.lexeme);
        }
        resolve(function.functionBody);
        endScope();
    }

    @Override
    public Void visitFunStmt(Stmt.Fun stmt) {
        declare(stmt.name.lexeme);
        define(stmt.name.lexeme);
        resolveFunction(stmt, FUNCTION);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        declare(stmt.name.lexeme);
        define(stmt.name.lexeme);

        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
        }

        if (stmt.superclass != null) {
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put("super", VariableState.DEFINED);
        }

        beginScope();

        scopes.peek().put("this", VariableState.DECLARED);

        for (final var method : stmt.methods) {
            final var declaration = FunctionType.METHOD;
            resolveFunction(method, declaration);
        }

        endScope();

        if (stmt.superclass != null) {
            endScope();
        }
        return null;
    }
}
