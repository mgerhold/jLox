package com.craftinginterpreters.lox;

public class RPNPrinter implements Expr.Visitor<String> {

    String getRPNRepresentation(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return getRPNRepresentation(expr.left) + " " + getRPNRepresentation(expr.right) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return getRPNRepresentation(expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) {
            return "nil";
        }
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return getRPNRepresentation(expr.right) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitConditionalExpr(Expr.Conditional expr) {
        return getRPNRepresentation(expr.condition) + " " + getRPNRepresentation(expr.thenBranch) + " "
                + getRPNRepresentation(expr.elseBranch) + " ?:";
    }
}
