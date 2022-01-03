package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Fun declaration;
    private final Environment closure;
    private final boolean isInitializer;

    LoxFunction(Stmt.Fun declaration, Environment closure, boolean isInitializer) {
        this.closure = closure;
        this.declaration = declaration;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance instance) {
        final var environment = new Environment(closure);
        environment.defineByName("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
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
            return isInitializer ? closure.getAt(0, "this") : e.getValue();
        }
        if (isInitializer) {
            return closure.getAt(0, "this");
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
