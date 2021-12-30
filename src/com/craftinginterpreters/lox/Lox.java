package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStreamReader;
import java.util.List;

public class Lox {

    static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(final String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(final String path) throws IOException {
        final byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError) {
            System.exit(65);
        }
        if (hadRuntimeError) {
            System.exit(70);
        }
    }

    private static void runPrompt() throws IOException {
        var input = new InputStreamReader(System.in);
        var reader = new BufferedReader(input);

        while (true) {
            hadError = false;

            System.out.print("> ");
            final var scanner = new Scanner(reader.readLine());
            final var tokens = scanner.scanTokens();
            final var parser = new Parser(tokens);
            final var syntax = parser.parseRepl();
            if (hadError) {
                continue;
            }
            if (syntax instanceof List) {
                interpreter.interpret((List<Stmt>)syntax);
            } else {
                final var result = interpreter.interpret((Expr)syntax);
                if (result != null) {
                    System.out.println("= " + result);
                }
            }
        }
    }

    private static void run(final String source) {
        final var scanner = new Scanner(source);
        final List<Token> tokens = scanner.scanTokens();

        var parser = new Parser(tokens);
        var statements = parser.parse();

        if (hadError) {
            return;
        }

        interpreter.interpret(statements);
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error " + where + ": " + message);
        hadError = true;
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        System.err.flush();
        hadRuntimeError = true;
    }

}
