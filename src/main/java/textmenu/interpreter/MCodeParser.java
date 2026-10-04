
package textmenu.interpreter;

import java.math.BigInteger;
import java.util.*;
import static textmenu.interpreter.MCodeAst.*;

public final class MCodeParser {
    private final List<MCodeToken> tokens;
    private int position;

    public MCodeParser(List<MCodeToken> tokens) { this.tokens=tokens; }

    public Program parse() {
        return new Program(List.copyOf(parseStatementsUntil(MCodeToken.Type.EOF)));
    }

    private List<Statement> parseStatementsUntil(MCodeToken.Type terminator) {
        List<Statement> out=new ArrayList<>();
        skipNewlines();
        while (!check(terminator) && !check(MCodeToken.Type.EOF)) {
            if (check(MCodeToken.Type.DEDENT)) {
                if (terminator==MCodeToken.Type.DEDENT) break;
                error(peek(),"DEDENT inesperado");
            }
            out.addAll(parseStatementLine());
            skipNewlines();
        }
        return out;
    }

    private List<Statement> parseStatementLine() {
        List<Statement> out=new ArrayList<>();
        out.add(parseStatement());
        while (matchText(";")) {
            if (check(MCodeToken.Type.NEWLINE)||check(MCodeToken.Type.EOF)||check(MCodeToken.Type.DEDENT)) break;
            out.add(parseStatement());
        }
        match(MCodeToken.Type.NEWLINE);
        return out;
    }

    private Statement parseStatement() {
        if (checkKeyword("if")) return parseIf();
        if (checkKeyword("while")) return parseWhile();
        if (checkKeyword("for")) return parseFor();
        if (checkKeyword("def")) return parseFunctionDef(false);
        if (checkKeyword("async") && lookaheadText(1, "def")) return parseFunctionDef(true);
        if (checkKeyword("class")) return parseClassDef();
        if (checkKeyword("try")) return parseTry();
        if (checkKeyword("match")) return parseMatch();
        if (checkKeyword("with")) return parseWith();
        if (checkKeyword("import")) return parseImport();
        if (checkKeyword("from")) return parseFromImport();
        if (checkKeyword("return")) return parseReturn();
        if (checkKeyword("raise")) return parseRaise();
        if (checkKeyword("global")) return parseGlobal();
        if (checkKeyword("nonlocal")) return parseNonlocal();
        if (checkKeyword("break")) { advance(); return new Break(); }
        if (checkKeyword("continue")) { advance(); return new Continue(); }
        if (checkKeyword("pass")) { advance(); return new Pass(); }
        if (checkKeyword("elif")||checkKeyword("else")||checkKeyword("except")||checkKeyword("finally")||checkKeyword("case"))
            error(peek(),"Sentencia inesperada '"+peek().text()+"'");
        return parseSimpleStatement();
    }

    private Statement parseIf() {
        List<IfBranch> branches=new ArrayList<>();
        consumeKeyword("if","Se esperaba 'if'");
        Expression condition=parseExpression();
        consumeText(":","Se esperaba ':' después de la condición");
        branches.add(new IfBranch(condition,parseSuite()));
        while (checkKeyword("elif")) {
            advance();
            Expression c=parseExpression();
            consumeText(":","Se esperaba ':' después de 'elif'");
            branches.add(new IfBranch(c,parseSuite()));
        }
        Block elseBlock=null;
        if (checkKeyword("else")) {
            advance(); consumeText(":","Se esperaba ':' después de 'else'");
            elseBlock=parseSuite();
        }
        return new If(List.copyOf(branches),elseBlock);
    }

    private Statement parseWhile() {
        consumeKeyword("while","Se esperaba 'while'");
        Expression condition=parseExpression();
        consumeText(":","Se esperaba ':' después de la condición");
        Block body=parseSuite(), elseBlock=null;
        if (checkKeyword("else")) {
            advance(); consumeText(":","Se esperaba ':' después de 'else'");
            elseBlock=parseSuite();
        }
        return new While(condition,body,elseBlock);
    }

    private Statement parseFor() {
        consumeKeyword("for","Se esperaba 'for'");
        Expression target=parseTargetList();
        consumeKeyword("in","Se esperaba 'in' en el for");
        Expression iterable=parseExpression();
        consumeText(":","Se esperaba ':' después del iterable");
        Block body=parseSuite(), elseBlock=null;
        if (checkKeyword("else")) {
            advance(); consumeText(":","Se esperaba ':' después de 'else'");
            elseBlock=parseSuite();
        }
        validateTarget(target);
        return new For(target,iterable,body,elseBlock);
    }

    private Statement parseFunctionDef(boolean async) {
        if (async) consumeKeyword("async","Se esperaba 'async'");
        consumeKeyword("def","Se esperaba 'def'");
        String name=consumeIdentifier("Se esperaba el nombre de la función");
        consumeText("(","Se esperaba '('");
        List<Parameter> parameters=parseParameters();
        consumeText(")","Se esperaba ')'");
        consumeText(":","Se esperaba ':' después de la definición");
        Block body=parseSuite();
        return async
                ? new AsyncFunctionDef(name,parameters.isEmpty()?List.of():List.copyOf(parameters),body)
                : new FunctionDef(name,parameters.isEmpty()?List.of():List.copyOf(parameters),body);
    }

    private List<Parameter> parseParameters() {
        List<Parameter> out=new ArrayList<>();
        boolean seenDefault=false, seenVarargs=false, seenKwargs=false;
        if (checkText(")")) return out;
        while (true) {
            if (matchText("**")) {
                if (seenKwargs) error(previous(),"Solo puede existir un **kwargs");
                seenKwargs=true;
                out.add(new Parameter(consumeIdentifier("Se esperaba nombre de **kwargs"),null,false,true));
            } else if (matchText("*")) {
                if (seenVarargs) error(previous(),"Solo puede existir un *args");
                seenVarargs=true;
                out.add(new Parameter(consumeIdentifier("Se esperaba nombre de *args"),null,true,false));
            } else {
                if (seenVarargs||seenKwargs) error(peek(),"No se pueden declarar parámetros normales después de *args/**kwargs");
                String name=consumeIdentifier("Se esperaba el nombre del parámetro");
                Expression def=null;
                if (matchText("=")) { seenDefault=true; def=parseExpression(); }
                else if (seenDefault) error(peek(),"Un parámetro sin valor por defecto no puede seguir a uno con valor por defecto");
                out.add(new Parameter(name,def,false,false));
            }
            if (!matchText(",")) break;
            if (checkText(")")) break;
        }
        return out;
    }

    private Statement parseClassDef() {
        consumeKeyword("class","Se esperaba 'class'");
        String name=consumeIdentifier("Se esperaba el nombre de la clase");
        List<Expression> bases=new ArrayList<>();
        if (matchText("(")) {
            if (!checkText(")")) {
                do { bases.add(parseExpression()); } while (matchText(",")&&!checkText(")"));
            }
            consumeText(")","Se esperaba ')' después de las clases base");
        }
        consumeText(":","Se esperaba ':' después del nombre de la clase");
        return new ClassDef(name,List.copyOf(bases),parseSuite());
    }

    private Statement parseMatch() {
        consumeKeyword("match","Se esperaba 'match'");
        Expression subject=parseExpression();
        consumeText(":","Se esperaba ':' después de 'match'");
        consume(MCodeToken.Type.NEWLINE,"Se esperaba nueva línea después de 'match'");
        consume(MCodeToken.Type.INDENT,"Se esperaba un bloque indentado para 'match'");
        List<MatchCase> cases=new ArrayList<>();
        skipNewlines();
        while (checkKeyword("case")) {
            advance();
            Expression pattern=parseExpression();
            Expression guard=null;
            if (checkKeyword("if")) { advance(); guard=parseExpression(); }
            consumeText(":","Se esperaba ':' después del patrón");
            cases.add(new MatchCase(pattern,guard,parseSuite()));
            skipNewlines();
        }
        consume(MCodeToken.Type.DEDENT,"Se esperaba el final de 'match'");
        if (cases.isEmpty()) error(peek(),"'match' necesita al menos un 'case'");
        return new Match(subject,List.copyOf(cases));
    }

    private Statement parseWith() {
        consumeKeyword("with","Se esperaba 'with'");
        List<WithItem> items=new ArrayList<>();
        do {
            Expression context=parseExpression();
            String asName=null;
            if (checkKeyword("as")) { advance(); asName=consumeIdentifier("Se esperaba nombre después de 'as'"); }
            items.add(new WithItem(context,asName));
        } while (matchText(","));
        consumeText(":","Se esperaba ':' después de 'with'");
        return new With(List.copyOf(items),parseSuite());
    }

    private Statement parseImport() {
        consumeKeyword("import","Se esperaba 'import'");
        List<ImportItem> items=new ArrayList<>();
        do {
            String module=parseDottedName();
            String alias=null;
            if (checkKeyword("as")) { advance(); alias=consumeIdentifier("Se esperaba alias después de 'as'"); }
            items.add(new ImportItem(module,alias));
        } while (matchText(","));
        return new Import(List.copyOf(items));
    }

    private Statement parseFromImport() {
        consumeKeyword("from","Se esperaba 'from'");
        String module=parseDottedName();
        consumeKeyword("import","Se esperaba 'import' después del módulo");
        if (matchText("*")) return new FromImport(module,List.of(),true);
        List<ImportItem> items=new ArrayList<>();
        do {
            String name=consumeIdentifier("Se esperaba nombre para importar");
            String alias=null;
            if (checkKeyword("as")) { advance(); alias=consumeIdentifier("Se esperaba alias después de 'as'"); }
            items.add(new ImportItem(name,alias));
        } while (matchText(","));
        return new FromImport(module,List.copyOf(items),false);
    }

    private String parseDottedName() {
        StringBuilder b=new StringBuilder(consumeIdentifier("Se esperaba nombre de módulo"));
        while (matchText(".")) b.append('.').append(consumeIdentifier("Se esperaba nombre después de '.'"));
        return b.toString();
    }

    private Statement parseTry() {
        consumeKeyword("try","Se esperaba 'try'");
        consumeText(":","Se esperaba ':' después de 'try'");
        Block body=parseSuite();
        List<ExceptHandler> handlers=new ArrayList<>();
        while (checkKeyword("except")) {
            advance();
            Expression type=null; String name=null;
            if (!checkText(":")) {
                type=parseExpression();
                if (checkKeyword("as")) { advance(); name=consumeIdentifier("Se esperaba nombre después de 'as'"); }
            }
            consumeText(":","Se esperaba ':' después de 'except'");
            handlers.add(new ExceptHandler(type,name,parseSuite()));
        }
        Block elseBlock=null, finallyBlock=null;
        if (checkKeyword("else")) { advance(); consumeText(":","Se esperaba ':' después de 'else'"); elseBlock=parseSuite(); }
        if (checkKeyword("finally")) { advance(); consumeText(":","Se esperaba ':' después de 'finally'"); finallyBlock=parseSuite(); }
        if (handlers.isEmpty()&&finallyBlock==null) error(peek(),"'try' necesita 'except' o 'finally'");
        return new Try(body,List.copyOf(handlers),elseBlock,finallyBlock);
    }

    private Statement parseReturn() {
        consumeKeyword("return","Se esperaba 'return'");
        if (check(MCodeToken.Type.NEWLINE)||check(MCodeToken.Type.DEDENT)||check(MCodeToken.Type.EOF)||checkText(";"))
            return new Return(null);
        return new Return(parseExpression());
    }

    private Statement parseRaise() {
        consumeKeyword("raise","Se esperaba 'raise'");
        if (check(MCodeToken.Type.NEWLINE)||check(MCodeToken.Type.DEDENT)||check(MCodeToken.Type.EOF)||checkText(";"))
            return new Raise(null);
        return new Raise(parseExpression());
    }

    private Statement parseGlobal() {
        consumeKeyword("global","Se esperaba 'global'");
        List<String> names=new ArrayList<>();
        do { names.add(consumeIdentifier("Se esperaba un nombre después de 'global'")); } while (matchText(","));
        return new Global(List.copyOf(names));
    }

    private Statement parseNonlocal() {
        consumeKeyword("nonlocal","Se esperaba 'nonlocal'");
        List<String> names=new ArrayList<>();
        do { names.add(consumeIdentifier("Se esperaba un nombre después de 'nonlocal'")); } while (matchText(","));
        return new Nonlocal(List.copyOf(names));
    }

    private Block parseSuite() {
        if (match(MCodeToken.Type.NEWLINE)) {
            consume(MCodeToken.Type.INDENT,"Se esperaba un bloque indentado");
            List<Statement> body=parseStatementsUntil(MCodeToken.Type.DEDENT);
            consume(MCodeToken.Type.DEDENT,"Se esperaba el final del bloque");
            return new Block(List.copyOf(body));
        }
        return new Block(List.copyOf(parseStatementLine()));
    }

    private Statement parseSimpleStatement() {
        Expression first=parseExpression();
        List<Expression> targets=new ArrayList<>(); targets.add(first);
        if (matchText(",")) {
            while (true) {
                targets.add(parseExpression());
                if (!matchText(",")) break;
                if (checkText("=")||check(MCodeToken.Type.NEWLINE)||check(MCodeToken.Type.EOF)||checkText(";")) break;
            }
        }

        if (matchText("=")) {
            validateTargets(targets);
            List<Expression> assignmentTargets=new ArrayList<>();
            assignmentTargets.add(targets.size()==1?first:new TupleExpr(List.copyOf(targets)));
            while (true) {
                Expression value=parseExpression();
                if (matchText("=")) {
                    if (assignmentTargets.size()!=1) error(previous(),"No se puede encadenar una asignación estructurada");
                    assignmentTargets.add(requireTarget(value)); continue;
                }
                if (matchText(",")) {
                    List<Expression> values=new ArrayList<>(); values.add(value);
                    while (!check(MCodeToken.Type.NEWLINE)&&!check(MCodeToken.Type.EOF)&&!checkText(";")) {
                        values.add(parseExpression());
                        if (!matchText(",")) break;
                    }
                    value=new TupleExpr(List.copyOf(values));
                }
                return new Assignment(List.copyOf(assignmentTargets),value);
            }
        }

        if (isAugmentedOperator(peek().text())) {
            String op=advance().text(); validateTarget(first);
            return new AugmentedAssignment(first,op.substring(0,op.length()-1),parseExpression());
        }
        if (targets.size()>1) error(peek(),"Se esperaba '=' después de la asignación múltiple");
        return new ExpressionStatement(first);
    }

    private Expression parseExpression() {
        if (checkKeyword("lambda")) return parseLambda();
        if (checkKeyword("yield")) return parseYield();
        if (checkKeyword("await")) { advance(); return new Await(parseExpression()); }
        return parseOr();
    }

    private Expression parseYield() {
        consumeKeyword("yield","Se esperaba 'yield'");
        if (checkKeyword("from")) {
            advance();
            return new Yield(parseExpression(),true);
        }
        if (check(MCodeToken.Type.NEWLINE)||check(MCodeToken.Type.DEDENT)||check(MCodeToken.Type.EOF)||checkText(";"))
            return new Yield(null,false);
        return new Yield(parseExpression(),false);
    }


    private Expression parseLambda() {
        consumeKeyword("lambda","Se esperaba 'lambda'");
        List<Parameter> params=new ArrayList<>();
        if (!checkText(":")) {
            boolean seenDefault=false;
            do {
                if (matchText("*")) params.add(new Parameter(consumeIdentifier("Se esperaba nombre de *args"),null,true,false));
                else if (matchText("**")) params.add(new Parameter(consumeIdentifier("Se esperaba nombre de **kwargs"),null,false,true));
                else {
                    String name=consumeIdentifier("Se esperaba nombre de parámetro");
                    Expression def=null;
                    if (matchText("=")) { seenDefault=true; def=parseExpression(); }
                    else if (seenDefault) error(peek(),"Parámetro sin valor por defecto después de uno con valor por defecto");
                    params.add(new Parameter(name,def,false,false));
                }
            } while (matchText(","));
        }
        consumeText(":","Se esperaba ':' en lambda");
        return new Lambda(List.copyOf(params),parseExpression());
    }

    private Expression parseOr() {
        Expression e=parseAnd();
        while (matchKeyword("or")) e=new Binary(e,"or",parseAnd());
        return e;
    }

    private Expression parseAnd() {
        Expression e=parseNot();
        while (matchKeyword("and")) e=new Binary(e,"and",parseNot());
        return e;
    }

    private Expression parseNot() { return matchKeyword("not")?new Unary("not",parseNot()):parseComparison(); }

    private Expression parseComparison() {
        Expression left=parseBitOr();
        List<String> ops=new ArrayList<>(); List<Expression> rights=new ArrayList<>();
        while (true) {
            if (matchText("==")) ops.add("==");
            else if (matchText("!=")) ops.add("!=");
            else if (matchText("<=")) ops.add("<=");
            else if (matchText(">=")) ops.add(">=");
            else if (matchText("<")) ops.add("<");
            else if (matchText(">")) ops.add(">");
            else if (matchKeyword("in")) ops.add("in");
            else if (matchKeyword("is")) ops.add(matchKeyword("not")?"is not":"is");
            else if (checkKeyword("not")&&lookaheadText(1,"in")) { advance(); advance(); ops.add("not in"); }
            else break;
            rights.add(parseBitOr());
        }
        return ops.isEmpty()?left:new Comparison(left,List.copyOf(ops),List.copyOf(rights));
    }

    private Expression parseBitOr() {
        Expression e=parseBitXor(); while(matchText("|")) e=new Binary(e,"|",parseBitXor()); return e;
    }
    private Expression parseBitXor() {
        Expression e=parseBitAnd(); while(matchText("^")) e=new Binary(e,"^",parseBitAnd()); return e;
    }
    private Expression parseBitAnd() {
        Expression e=parseShift(); while(matchText("&")) e=new Binary(e,"&",parseShift()); return e;
    }
    private Expression parseShift() {
        Expression e=parseTerm(); while(checkText("<<")||checkText(">>")) { String op=advance().text(); e=new Binary(e,op,parseTerm()); } return e;
    }
    private Expression parseTerm() {
        Expression e=parseFactor(); while(checkText("+")||checkText("-")) { String op=advance().text(); e=new Binary(e,op,parseFactor()); } return e;
    }
    private Expression parseFactor() {
        Expression e=parseUnary(); while(checkText("*")||checkText("/")||checkText("//")||checkText("%")||checkText("@")) { String op=advance().text(); e=new Binary(e,op,parseUnary()); } return e;
    }
    private Expression parseUnary() {
        if(matchText("+")) return new Unary("+",parseUnary());
        if(matchText("-")) return new Unary("-",parseUnary());
        if(matchText("~")) return new Unary("~",parseUnary());
        return parsePower();
    }
    private Expression parsePower() {
        Expression e=parsePostfix();
        if(matchText("**")) e=new Binary(e,"**",parseUnary());
        return e;
    }

    private Expression parsePostfix() {
        Expression e=parsePrimary();
        while (true) {
            if(matchText("(")) { e=new Call(e,parseArguments()); continue; }
            if(matchText("[")) { Expression sub=parseSubscript(); consumeText("]","Se esperaba ']'"); e=new Subscript(e,sub); continue; }
            if(matchText(".")) { e=new Attribute(e,consumeIdentifier("Se esperaba un nombre después de '.'")); continue; }
            break;
        }
        return e;
    }

    private List<CallArg> parseArguments() {
        List<CallArg> args=new ArrayList<>();
        if(matchText(")")) return args;
        do {
            if(matchText("**")) args.add(new CallArg(null,parseExpression(),false,true));
            else if(matchText("*")) args.add(new CallArg(null,parseExpression(),true,false));
            else {
                Expression v=parseExpression();
                if(v instanceof Name n&&matchText("=")) args.add(new CallArg(n.name(),parseExpression(),false,false));
                else args.add(new CallArg(null,v,false,false));
            }
        } while(matchText(",")&&!checkText(")"));
        consumeText(")","Se esperaba ')'");
        return List.copyOf(args);
    }

    private Expression parseSubscript() {
        Expression start=null,stop=null,step=null;
        if(!checkText(":")&&!checkText("]")) start=parseExpression();
        if(matchText(":")) {
            if(!checkText(":")&&!checkText("]")) stop=parseExpression();
            if(matchText(":")&&!checkText("]")) step=parseExpression();
            return new Slice(start,stop,step);
        }
        if(start==null) error(peek(),"Índice vacío");
        return start;
    }

    private Expression parsePrimary() {
        MCodeToken t=advance();
        if(t.type()==MCodeToken.Type.NUMBER) return new Literal(parseNumber(t));
        if(t.type()==MCodeToken.Type.STRING) return new Literal(parseString(t));
        if(t.type()==MCodeToken.Type.KEYWORD) {
            return switch(t.text()) {
                case "True"->new Literal(Boolean.TRUE);
                case "False"->new Literal(Boolean.FALSE);
                case "None"->new Literal(null);
                default->{ error(t,"Se esperaba una expresión, no '"+t.text()+"'"); yield null; }
            };
        }
        if(t.type()==MCodeToken.Type.IDENTIFIER) return new Name(t.text());
        if(t.text().equals("(")) return parseParenthesized();
        if(t.text().equals("[")) return parseList();
        if(t.text().equals("{")) return parseBraceLiteral();
        error(t,"Se esperaba una expresión"); return null;
    }

    private Expression parseParenthesized() {
        if(matchText(")")) return new TupleExpr(List.of());
        Expression first=parseExpression();
        if(checkKeyword("for")) {
            List<Comprehension> gs=parseComprehensionTail();
            consumeText(")","Se esperaba ')'");
            return new ListComp(first,gs);
        }
        if(!matchText(",")) { consumeText(")","Se esperaba ')'"); return first; }
        List<Expression> xs=new ArrayList<>(); xs.add(first);
        while(!checkText(")")) { xs.add(parseExpression()); if(!matchText(",")) break; }
        consumeText(")","Se esperaba ')'");
        return new TupleExpr(List.copyOf(xs));
    }

    private Expression parseList() {
        if(matchText("]")) return new ListExpr(List.of());
        Expression first=parseExpression();
        if(checkKeyword("for")) {
            List<Comprehension> gs=parseComprehensionTail(); consumeText("]","Se esperaba ']'");
            return new ListComp(first,gs);
        }
        List<Expression> xs=new ArrayList<>(); xs.add(first);
        while(matchText(",")&&!checkText("]")) xs.add(parseExpression());
        consumeText("]","Se esperaba ']'");
        return new ListExpr(List.copyOf(xs));
    }

    private Expression parseBraceLiteral() {
        if(matchText("}")) return new DictExpr(List.of());
        Expression first=parseExpression();
        if(matchText(":")) {
            Expression firstValue=parseExpression();
            if(checkKeyword("for")) {
                List<Comprehension> gs=parseComprehensionTail(); consumeText("}","Se esperaba '}'");
                return new DictComp(first,firstValue,gs);
            }
            List<Entry> es=new ArrayList<>(); es.add(new Entry(first,firstValue));
            while(matchText(",")&&!checkText("}")) {
                Expression key=parseExpression(); consumeText(":","Se esperaba ':' en el diccionario");
                es.add(new Entry(key,parseExpression()));
            }
            consumeText("}","Se esperaba '}'");
            return new DictExpr(List.copyOf(es));
        }
        if(checkKeyword("for")) {
            List<Comprehension> gs=parseComprehensionTail(); consumeText("}","Se esperaba '}'");
            return new SetComp(first,gs);
        }
        List<Expression> xs=new ArrayList<>(); xs.add(first);
        while(matchText(",")&&!checkText("}")) xs.add(parseExpression());
        consumeText("}","Se esperaba '}'");
        return new SetExpr(List.copyOf(xs));
    }

    private List<Comprehension> parseComprehensionTail() {
        List<Comprehension> gs=new ArrayList<>();
        do {
            consumeKeyword("for","Se esperaba 'for' en la comprehensión");
            Expression target=parseTargetList();
            consumeKeyword("in","Se esperaba 'in' en la comprehensión");
            Expression iterable=parseExpression();
            List<Expression> conditions=new ArrayList<>();
            while(checkKeyword("if")) { advance(); conditions.add(parseExpression()); }
            gs.add(new Comprehension(target,iterable,List.copyOf(conditions)));
        } while(checkKeyword("for"));
        return List.copyOf(gs);
    }

    private Expression parseTargetList() {
        Expression first=parseTargetAtom();
        if(!matchText(",")) return first;
        List<Expression> xs=new ArrayList<>(); xs.add(first);
        while(true) {
            if(checkKeyword("in")||checkText(":")||checkText(")")||checkText("]")||checkText("}")) break;
            xs.add(parseTargetAtom());
            if(!matchText(",")) break;
        }
        return new TupleExpr(List.copyOf(xs));
    }

    private Expression parseTargetAtom() {
        if(matchText("(")) {
            Expression x=parseTargetList(); consumeText(")","Se esperaba ')'"); return x;
        }
        if(matchText("[")) {
            Expression x=parseTargetList(); consumeText("]","Se esperaba ']'"); return x;
        }
        Expression result=new Name(consumeIdentifier("Se esperaba un nombre de destino"));
        while(true) {
            if(matchText(".")) result=new Attribute(result,consumeIdentifier("Se esperaba un atributo"));
            else if(matchText("[")) { Expression s=parseSubscript(); consumeText("]","Se esperaba ']'"); result=new Subscript(result,s); }
            else break;
        }
        return result;
    }

    private void validateTargets(List<Expression> targets) { for(Expression e:targets) validateTarget(e); }

    private void validateTarget(Expression e) {
        if(e instanceof TupleExpr t) { for(Expression x:t.elements()) validateTarget(x); return; }
        if(e instanceof ListExpr l) { for(Expression x:l.elements()) validateTarget(x); return; }
        if(!(e instanceof Name||e instanceof Attribute||e instanceof Subscript))
            error(peek(),"Objetivo de asignación inválido");
    }

    private Expression requireTarget(Expression e) { validateTarget(e); return e; }

    private boolean isAugmentedOperator(String text) {
        return switch(text) {
            case "+=","-=","*=","/=","//=","%=","**=","&=","|=","^=","<<=",">>=","@="->true;
            default->false;
        };
    }

    private boolean matchKeyword(String k) { if(checkKeyword(k)){advance();return true;} return false; }
    private void consumeKeyword(String k,String m){ if(!matchKeyword(k)) error(peek(),m); }
    private boolean checkKeyword(String k){ return (check(MCodeToken.Type.KEYWORD)||check(MCodeToken.Type.IDENTIFIER))&&k.equals(peek().text()); }
    private String consumeIdentifier(String m){ if(check(MCodeToken.Type.IDENTIFIER)) return advance().text(); error(peek(),m); return null; }
    private boolean matchText(String s){ if(checkText(s)){advance();return true;} return false; }
    private boolean checkText(String s){ return s.equals(peek().text()); }
    private boolean lookaheadText(int n,String s){ return s.equals(tokens.get(Math.min(position+n,tokens.size()-1)).text()); }
    private boolean check(MCodeToken.Type t){ return peek().type()==t; }
    private MCodeToken advance(){ return position<tokens.size()?tokens.get(position++):tokens.get(tokens.size()-1); }
    private MCodeToken previous(){ return tokens.get(Math.max(0,position-1)); }
    private MCodeToken peek(){ return tokens.get(Math.min(position,tokens.size()-1)); }
    private void skipNewlines(){ while(match(MCodeToken.Type.NEWLINE)){} }
    private boolean match(MCodeToken.Type t){ if(check(t)){advance();return true;} return false; }
    private void consume(MCodeToken.Type t,String m){ if(!match(t)) error(peek(),m); }
    private void consumeText(String s,String m){ if(!matchText(s)) error(peek(),m); }
    private void error(MCodeToken t,String m){ throw new ParseException(m,t.line(),t.column()); }

    private static Object parseNumber(MCodeToken token) {
        String raw=token.text().replace("_","");
        try {
            if(raw.startsWith("0x")||raw.startsWith("0X")) return new BigInteger(raw.substring(2),16);
            if(raw.startsWith("0b")||raw.startsWith("0B")) return new BigInteger(raw.substring(2),2);
            if(raw.startsWith("0o")||raw.startsWith("0O")) return new BigInteger(raw.substring(2),8);
            if(raw.contains(".")||raw.contains("e")||raw.contains("E")) return Double.parseDouble(raw);
            return new BigInteger(raw);
        } catch(NumberFormatException e) {
            throw new ParseException("Número inválido: "+token.text(),token.line(),token.column());
        }
    }

    private static String parseString(MCodeToken token) {
        String raw=token.text();
        String body=raw.substring(1,raw.length()-1);
        StringBuilder out=new StringBuilder(); boolean escaped=false;
        for(int i=0;i<body.length();i++) {
            char c=body.charAt(i);
            if(escaped) {
                out.append(switch(c) {
                    case 'n'->'\n'; case 'r'->'\r'; case 't'->'\t'; case 'b'->'\b'; case 'f'->'\f';
                    case '\\'->'\\'; case '\''->'\''; case '"'->'"'; default->c;
                }); escaped=false;
            } else if(c=='\\') escaped=true;
            else out.append(c);
        }
        if(escaped) out.append('\\');
        return out.toString();
    }

    public static class ParseException extends RuntimeException {
        private final int line,column;
        public ParseException(String message,int line,int column){
            super("Línea "+line+", columna "+column+": "+message);
            this.line=line; this.column=column;
        }
        public int line(){return line;}
        public int column(){return column;}
    }
}
