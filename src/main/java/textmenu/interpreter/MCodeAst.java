package textmenu.interpreter;

import java.util.List;

public final class MCodeAst {
    private MCodeAst() {}

    public interface Node {}

    public interface Statement extends Node {}
    public interface Expression extends Node {}
    public interface Target extends Expression {}

    public record Program(List<Statement> statements) implements Node {}
    public record ExpressionStatement(Expression expression) implements Statement {}
    public record Assignment(List<Expression> targets, Expression value) implements Statement {}
    public record AugmentedAssignment(Expression target, String operator, Expression value) implements Statement {}

    public record Literal(Object value) implements Expression {}
    public record Name(String name) implements Target {}
    public record ListExpr(List<Expression> elements) implements Expression {}
    public record TupleExpr(List<Expression> elements) implements Expression {}
    public record SetExpr(List<Expression> elements) implements Expression {}
    public record DictExpr(List<Entry> entries) implements Expression {}
    public record Entry(Expression key, Expression value) {}
    public record Unary(String operator, Expression expression) implements Expression {}
    public record Binary(Expression left, String operator, Expression right) implements Expression {}
    public record Comparison(Expression left, List<String> operators, List<Expression> rights) implements Expression {}
    public record Call(Expression callee, List<CallArg> arguments) implements Expression {}
    public record CallArg(String name, Expression value, boolean unpack, boolean keywordUnpack) {}
    public record Index(Expression target, Expression index) implements Target {}
    public record Slice(Expression start, Expression stop, Expression step) implements Expression {}
    public record Subscript(Expression target, Expression subscript) implements Target {}
}
