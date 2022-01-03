package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    private static class LoopBreak extends RuntimeException { }
    private static class LoopContinue extends RuntimeException { }
    public static class Return extends RuntimeException {
        private final Object value;

        public Return(Object value) {
            super(null, null, false, false);
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final HashMap<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.defineByName("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (final var statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    public String interpret(Expr expression) {
        try {
            var value = evaluate(expression);
            return stringify(value);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        final var value = evaluate(expr.value);

        final var distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        final var left = evaluate(expr.left);
        final var right = evaluate(expr.right);

        switch (expr.operator.type) {
            case COMMA:
                return right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0.0) {
                    throw new RuntimeError(expr.operator, "Division by 0.");
                }
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }

                if (left instanceof String) {
                    // "1" + true = "1true"
                    return (String)left + stringify(right);
                }

                if (right instanceof String) {
                    // 1 + "true" = "1true"
                    return stringify(left) + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operator '+' is only supported for numbers and strings.");
            default:
                return null; // unreachable
        }
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        final var leftEvaluated = evaluate(expr.left);
        final var leftTruthyness = isTruthy(leftEvaluated);
        switch (expr.operator.type) {
            case AND:
                if (!leftTruthyness) {
                    return leftEvaluated;
                }
                return evaluate(expr.right);
            case OR:
                if (leftTruthyness) {
                    return leftEvaluated;
                }
                return evaluate(expr.right);
            default:
                return null; // unreachable
        }
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        final var callee = evaluate(expr.callee);

        final var arguments = new ArrayList<Object>();
        for (final var expression : expr.arguments) {
            arguments.add(evaluate(expression));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        final var function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments, got "
                    + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        // myObject.getOtherObject().property
        final var object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance)object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Cannot access property on non-class-instance.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        final var value = evaluate(expr.value);
        final var object = evaluate(expr.object);

        if (object instanceof LoxInstance) {
            final var instance = (LoxInstance)object;
            instance.set(expr.name, value);
            return value;
        }

        throw new RuntimeError(expr.name, "Expression does not evaluate to an instance of an object.");
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        final var distance = locals.get(expr);
        final var superclass = (LoxClass)environment.getAt(distance, "super");
        final var object = (LoxInstance)environment.getAt(distance - 1, "this");
        final var method = superclass.findMethod(expr.method.lexeme);
        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        final var evaluated = evaluate(expr.right);
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, evaluated);
                return -(double) evaluated;
            case BANG:
                return !isTruthy(evaluated);
            default:
                return null; // unreachable
        }
    }

    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {
        return isTruthy(evaluate(expr.condition)) ? evaluate(expr.thenBranch) : evaluate(expr.elseBranch);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expression) {
        final var distance = locals.get(expression);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        }

        if (!globals.contains(name)) {
            throw new RuntimeError(name, "Use of undeclared variable '" + name.lexeme + "'.");
        }
        return globals.get(name);
    }

    private Object evaluate(Expr expression) {
        return expression.accept(this);
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        return true;
    }

    private static boolean isEqual(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null) {
            return false;
        }
        // make +0.0 == -0.0 evaluate to true (equals() would evaluate to false)
        if (left instanceof Double && right instanceof Double) {
            return (double)left == (double)right;
        }
        return left.equals(right);
    }

    private static String stringify(Object object) {
        if (object == null) {
            return "nil";
        }

        if (object instanceof Double) {
            if ((double)object == 0.0) { // print -0.0 as 0
                return "0";
            }
            var text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private static void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private static void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new LoopBreak();
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        throw new LoopContinue();
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        throw new Return(stmt.value != null ? evaluate(stmt.value) : null);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        final var conditionSatisfied = isTruthy(evaluate(stmt.condition));
        if (conditionSatisfied) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.loopCondition))) {
            try {
                execute(stmt.loopBody);
            } catch (LoopBreak e) {
                break;
            } catch (LoopContinue e) {
                continue;
            }
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        System.out.println(stringify(evaluate(stmt.expression)));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer == null) {
            environment.define(stmt.name);
            return null;
        }
        environment.define(stmt.name, evaluate(stmt.initializer));
        return null;
    }

    @Override
    public Void visitFunStmt(Stmt.Fun stmt) {
        final var function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name, function);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }
        environment.define(stmt.name);

        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.defineByName("super", superclass);
        }

        final var methods = new HashMap<String, LoxFunction>();
        for (final var method : stmt.methods) {
            final var function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }
        final var klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);

        if (stmt.superclass != null) {
            environment = environment.enclosing;
        }

        environment.assign(stmt.name, klass);
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (final var statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }
}
