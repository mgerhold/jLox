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
        define(name, new Uninitialized());
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

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    private Environment ancestor(int distance) {
        var current = this;
        for (int i = 0; i < distance; ++i) {
            assert current != null;
            current = current.enclosing;
        }
        assert current != null;
        return current;
    }

    boolean contains(Token name) {
        if (values.containsKey(name.lexeme)) {
            return true;
        }

        if (enclosing != null) {
            return enclosing.contains(name);
        }

        return false;
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

    void assignAt(int distance, Token name, Object value) {
        final var environment = ancestor(distance);
        if (!environment.values.containsKey(name.lexeme)) {
            throw new RuntimeError(name, "Trying to assign to an undefined variable '" + name.lexeme + "'.");
        }
        environment.values.put(name.lexeme, value);
    }
}
