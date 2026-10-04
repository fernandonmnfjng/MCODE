package textmenu.interpreter;

import java.util.List;

public final class MCodeAst {
    private MCodeAst() {}

    public interface Node {}
    public interface Statement extends Node {}
    public interface Expression extends Node {}
    public interface Target extends Expression {}

    public record Program(List<Statement> statements) implements Node {}
    public record Block(List<Statement> statements) implements Node {}

    public record ExpressionStatement(Expression expression) implements Statement {}
    public record Assignment(List<Expression> targets, Expression value) implements Statement {}
    public record AugmentedAssignment(Expression target, String operator, Expression value) implements Statement {}

    public record If(List<IfBranch> branches, Block elseBlock) implements Statement {}
    public record IfBranch(Expression condition, Block body) implements Node {}
    public record While(Expression condition, Block body, Block elseBlock) implements Statement {}
    public record For(Expression target, Expression iterable, Block body, Block elseBlock) implements Statement {}

    public record FunctionDef(String name, List<Parameter> parameters, Block body) implements Statement {}
    public record AsyncFunctionDef(String name, List<Parameter> parameters, Block body) implements Statement {}
    public record Parameter(String name, Expression defaultValue, boolean varargs, boolean kwargs) implements Node {}
    public record Return(Expression value) implements Statement {}
    public record Break() implements Statement {}
    public record Continue() implements Statement {}
    public record Pass() implements Statement {}

    public record ClassDef(String name, List<Expression> bases, Block body) implements Statement {}

    public record Try(Block body, List<ExceptHandler> handlers, Block elseBlock, Block finallyBlock) implements Statement {}
    public record ExceptHandler(Expression type, String name, Block body) implements Node {}
    public record Raise(Expression value) implements Statement {}
    public record Global(List<String> names) implements Statement {}
    public record Nonlocal(List<String> names) implements Statement {}
    public record ImportItem(String name, String alias) implements Node {}
    public record Import(List<ImportItem> items) implements Statement {}
    public record FromImport(String module, List<ImportItem> items, boolean star) implements Statement {}
    public record Match(Expression subject, List<MatchCase> cases) implements Statement {}
    public record MatchCase(Expression pattern, Expression guard, Block body) implements Node {}
    public record With(List<WithItem> items, Block body) implements Statement {}
    public record WithItem(Expression context, String asName) implements Node {}

    public record Literal(Object value) implements Expression {}
    public record Name(String name) implements Target {}
    public record Attribute(Expression target, String name) implements Target {}
    public record ListExpr(List<Expression> elements) implements Expression {}
    public record TupleExpr(List<Expression> elements) implements Expression {}
    public record SetExpr(List<Expression> elements) implements Expression {}
    public record DictExpr(List<Entry> entries) implements Expression {}
    public record Entry(Expression key, Expression value) {}

    public record ListComp(Expression element, List<Comprehension> generators) implements Expression {}
    public record SetComp(Expression element, List<Comprehension> generators) implements Expression {}
    public record DictComp(Expression key, Expression value, List<Comprehension> generators) implements Expression {}
    public record Comprehension(Expression target, Expression iterable, List<Expression> conditions) implements Node {}

    public record Unary(String operator, Expression expression) implements Expression {}
    public record Binary(Expression left, String operator, Expression right) implements Expression {}
    public record Comparison(Expression left, List<String> operators, List<Expression> rights) implements Expression {}
    public record Call(Expression callee, List<CallArg> arguments) implements Expression {}
    public record CallArg(String name, Expression value, boolean unpack, boolean keywordUnpack) implements Node {}
    public record Subscript(Expression target, Expression subscript) implements Target {}
    public record Slice(Expression start, Expression stop, Expression step) implements Expression {}
    public record Lambda(List<Parameter> parameters, Expression body) implements Expression {}
    public record Await(Expression expression) implements Expression {}
    public record Yield(Expression expression, boolean from) implements Expression {}
}
