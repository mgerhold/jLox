package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private static class Uninitialized {
    }

    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(Token name) {
        define(name, null);
    }

    void define(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            throw new RuntimeError(name, "A variable named '" + name.lexeme +  "' has already been declared before.");
        }
        values.put(name.lexeme, value);
    }

    void defineByName(String name, Object value) {
        assert !values.containsKey(name);
        values.put(name, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            final var value = values.get(name.lexeme);
            if (value instanceof Uninitialized) {
                throw new RuntimeError(name, "Variable '" + name.lexeme + "' cannot be used before it is initialized.");
            }
            return value;
        }

        if (enclosing != null) {
            return enclosing.get(name);
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }
}
