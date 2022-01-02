package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Fun declaration;
    private final Environment closure;

    LoxFunction(Stmt.Fun declaration, Environment closure) {
        this.closure = closure;
        this.declaration = declaration;
    }

    LoxFunction bind(LoxInstance instance) {
        final var environment = new Environment(closure);
        environment.defineByName("this", instance);
        return new LoxFunction(declaration, environment);
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        final var environment = new Environment(closure);
        for (int i = 0; i < declaration.parameters.size(); ++i) {
            environment.define(declaration.parameters.get(i), arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.functionBody, environment);
        } catch (Interpreter.Return e) {
            return e.getValue();
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
