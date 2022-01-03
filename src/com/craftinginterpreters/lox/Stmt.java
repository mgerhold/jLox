package com.craftinginterpreters.lox;

import javax.annotation.processing.Generated;
import java.util.List;

@Generated("Tools/GenerateAst.py")
abstract class Stmt {

  interface Visitor<R> {
    R visitExpressionStmt(Expression stmt);
    R visitIfStmt(If stmt);
    R visitWhileStmt(While stmt);
    R visitPrintStmt(Print stmt);
    R visitBlockStmt(Block stmt);
    R visitBreakStmt(Break stmt);
    R visitContinueStmt(Continue stmt);
    R visitReturnStmt(Return stmt);
    R visitVarStmt(Var stmt);
    R visitFunStmt(Fun stmt);
    R visitClassStmt(Class stmt);
  }

  static class Expression extends Stmt {
    Expression(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

    final Expr expression;
  }

  static class If extends Stmt {
    If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfStmt(this);
    }

    final Expr condition;
    final Stmt thenBranch;
    final Stmt elseBranch;
  }

  static class While extends Stmt {
    While(Expr loopCondition, Stmt loopBody) {
      this.loopCondition = loopCondition;
      this.loopBody = loopBody;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitWhileStmt(this);
    }

    final Expr loopCondition;
    final Stmt loopBody;
  }

  static class Print extends Stmt {
    Print(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitPrintStmt(this);
    }

    final Expr expression;
  }

  static class Block extends Stmt {
    Block(List<Stmt> statements) {
      this.statements = statements;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockStmt(this);
    }

    final List<Stmt> statements;
  }

  static class Break extends Stmt {
    Break() {
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBreakStmt(this);
    }

  }

  static class Continue extends Stmt {
    Continue() {
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitContinueStmt(this);
    }

  }

  static class Return extends Stmt {
    Return(Expr value) {
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnStmt(this);
    }

    final Expr value;
  }

  static class Var extends Stmt {
    Var(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitVarStmt(this);
    }

    final Token name;
    final Expr initializer;
  }

  static class Fun extends Stmt {
    Fun(Token name, List<Token> parameters, List<Stmt> functionBody) {
      this.name = name;
      this.parameters = parameters;
      this.functionBody = functionBody;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunStmt(this);
    }

    final Token name;
    final List<Token> parameters;
    final List<Stmt> functionBody;
  }

  static class Class extends Stmt {
    Class(Token name, Expr.Variable superclass, List<Stmt.Fun> methods) {
      this.name = name;
      this.superclass = superclass;
      this.methods = methods;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitClassStmt(this);
    }

    final Token name;
    final Expr.Variable superclass;
    final List<Stmt.Fun> methods;
  }

  abstract <R> R accept(Visitor<R> visitor);

}
