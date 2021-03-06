import sys


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def write_line(file, line=""):
    file.write(f"{line}\n")


def define_type(file, baseName, className, fieldList):
    write_line(file)
    write_line(file, f"  static class {className} extends {baseName} {{")
    # constructor
    write_line(file, f"    {className}({fieldList}) {{")
    fields = fieldList.split(",")
    if len(fields) == 1 and len(fields[0].strip()) == 0:
        fields = []
    for field in fields:
        name = field.strip().split(" ")[1]
        write_line(file, f"      this.{name} = {name};")
    write_line(file, "    }")

    # visitor pattern
    write_line(file)
    write_line(file, "    @Override")
    write_line(file, "    <R> R accept(Visitor<R> visitor) {")
    write_line(file, f"      return visitor.visit{className}{baseName}(this);")
    write_line(file, "    }")

    # fields
    write_line(file)
    for field in fields:
        write_line(file, f"    final {field.strip()};")
    write_line(file, "  }")


def define_visitor(file, baseName, types):
    write_line(file)
    write_line(file, "  interface Visitor<R> {")
    for type in types:
        typeName = type.split(":")[0].strip()
        write_line(file,
                   f"    R visit{typeName}{baseName}({typeName} {baseName.lower()});")
    write_line(file, "  }")


def define_ast(outputDir, baseName, types):
    path = f"{outputDir}/{baseName}.java"
    with open(path, "w") as file:
        write_line(file, "package com.craftinginterpreters.lox;")
        write_line(file)
        write_line(file, "import javax.annotation.processing.Generated;")
        write_line(file, "import java.util.List;")
        write_line(file)
        write_line(file, "@Generated(\"Tools/GenerateAst.py\")")
        write_line(file, f"abstract class {baseName} {{")
        define_visitor(file, baseName, types)

        # The AST classes
        for type in types:
            className = type.split(":")[0].strip()
            fields = type.split(":")[1].strip()
            define_type(file, baseName, className, fields)

        # The base accept() method.
        write_line(file)
        write_line(file, "  abstract <R> R accept(Visitor<R> visitor);")

        write_line(file)
        write_line(file, "}")


def main():
    if len(sys.argv) != 2:
        eprint(f"Usage: python {sys.argv[0]} <output directory>")
    else:
        outputDir = sys.argv[1]
        define_ast(outputDir, "Expr", [
            "Assign      : Token name, Expr value",
            "Binary      : Expr left, Token operator, Expr right",
            "Logical     : Expr left, Token operator, Expr right",
            "Call        : Expr callee, Token paren, List<Expr> arguments",
            "Get         : Expr object, Token name",
            "Set         : Expr object, Token name, Expr value",
            "This        : Token keyword",
            "Super       : Token keyword, Token method",
            "Grouping    : Expr expression",
            "Literal     : Object value",
            "Unary       : Token operator, Expr right",
            "Conditional : Expr condition, Expr thenBranch, Expr elseBranch",
            "Variable    : Token name"
        ])
        define_ast(outputDir, "Stmt", [
            "Expression  : Expr expression",
            "If          : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "While       : Expr loopCondition, Stmt loopBody",
            "Print       : Expr expression",
            "Block       : List<Stmt> statements",
            "Break       :",
            "Continue    :",
            "Return      : Expr value",
            "Var         : Token name, Expr initializer",
            "Fun         : Token name, List<Token> parameters, List<Stmt> functionBody",
            "Class       : Token name, Expr.Variable superclass, List<Stmt.Fun> methods"
        ])


if __name__ == "__main__":
    main()
