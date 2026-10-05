
package textmenu.interpreter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static textmenu.interpreter.MCodeAst.*;

public final class MCodeInterpreter {
    private static final StringBuilder OUTPUT=new StringBuilder();
    private static Map<String,Object> lastVariables=new LinkedHashMap<>();
    private static final Map<String,MExceptionType> EXCEPTION_TYPES=new LinkedHashMap<>();
    private static final MClass OBJECT_CLASS=new MClass("object",List.of(),Map.of());

    private static final List<String> BUILTIN_NAMES=List.of(
            "print","len","type","int","float","str","bool","list","tuple","set","dict",
            "range","abs","round","min","max","sum","any","all","enumerate","zip",
            "sorted","reversed","isinstance","issubclass","repr","ascii","format","hex","oct",
            "bin","ord","chr","hash","id","callable","dir","getattr","setattr","hasattr",
            "delattr","vars","object","super","sleep","pow","divmod","iter","next","map","filter"
    );

    private static final List<String> KEYWORD_NAMES=List.of(
            "if","elif","else","while","for","in","and","or","not","is","def","return",
            "break","continue","pass","class","try","except","finally","raise","global",
            "nonlocal","lambda","as","with","yield","from","import","async","await","match","case"
    );

    private MCodeInterpreter(){}

    public static String execute(String code){
        return execute(MCodeProjectManager.getWorkspaceRoot(), code);
    }

    public static String execute(Path workspaceRoot,String code){
        OUTPUT.setLength(0);
        Runtime runtime=new Runtime(workspaceRoot);
        ACTIVE.set(runtime);
        try{
            Program program=new MCodeParser(new MCodeLexer().tokenize(code)).parse();
            runtime.installBuiltins();
            runtime.executeProgram(program);
            lastVariables=runtime.global.snapshot();
            return OUTPUT.toString();
        }catch(RuntimeException e){
            OUTPUT.append("Error: ").append(errorMessage(e)).append('\n');
            return OUTPUT.toString();
        }finally{
            ACTIVE.remove();
        }
    }

    public static boolean validate(String code){
        try{
            new MCodeParser(new MCodeLexer().tokenize(code)).parse();
            return true;
        }catch(RuntimeException e){ return false; }
    }

    public static List<String> getErrors(String code){
        try{
            new MCodeParser(new MCodeLexer().tokenize(code)).parse();
            return List.of();
        }catch(RuntimeException e){
            return List.of(errorMessage(e));
        }
    }

    public static Map<String,Object> getVariables(){
        return Collections.unmodifiableMap(lastVariables);
    }

    public static void clearVariables(){ lastVariables=new LinkedHashMap<>(); }

    public static List<String> getBuiltinNames(){ return BUILTIN_NAMES; }
    public static List<String> getKeywordNames(){ return KEYWORD_NAMES; }

    public static Object resolveCompletionExpression(String expression){
        if(expression==null||expression.isBlank())return null;
        String[] parts=expression.split("\\.");
        Object value=lastVariables.get(parts[0]);
        if(value==null)return null;
        for(int i=1;i<parts.length;i++){
            try{ value=getAttribute(value,parts[i]); }catch(RuntimeException e){ return null; }
        }
        return value;
    }

    public static boolean isCompletionCallable(Object value){ return value instanceof Callable; }

    public static List<String> getCompletionMembers(Object value){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(value instanceof MModule m){ out.addAll(m.attrs.keySet()); }
        else if(value instanceof MInstance i){ out.addAll(i.fields.keySet()); collectClassMembers(i.clazz,out); }
        else if(value instanceof MClass c){ collectClassMembers(c,out); out.add("__name__"); }
        else if(value instanceof MFunction f){ out.add("__name__"); out.add("__call__"); for(Parameter p:f.parameters) out.add(p.name()); }
        else if(value instanceof String){ out.addAll(List.of("upper","lower","strip","replace","split","join","startswith","endswith","find","count","length")); }
        else if(value instanceof List){ out.addAll(List.of("append","extend","insert","pop","remove","clear","reverse","sort","index","count")); }
        else if(value instanceof Map){ out.addAll(List.of("get","keys","values","items","pop","update","clear")); }
        else if(value instanceof Set){ out.addAll(List.of("add","remove","discard","clear","union","intersection")); }
        else if(value instanceof TupleValue){ out.addAll(List.of("count","index")); }
        else if(value instanceof MRange){ out.add("start"); out.add("stop"); out.add("step"); }
        return new ArrayList<>(out);
    }

    private static void collectClassMembers(MClass c,Set<String> out){
        out.addAll(c.attrs.keySet());
        for(MClass b:c.bases) collectClassMembers(b,out);
    }

    public static List<String> getLastOutputLines(){
        if(OUTPUT.isEmpty()) return List.of();
        String s=OUTPUT.toString().replace("\r\n","\n").replace('\r','\n');
        String[] p=s.split("\n",-1);
        int n=p.length;
        if(n>0&&p[n-1].isEmpty()) n--;
        return List.of(Arrays.copyOf(p,n));
    }

    private static String errorMessage(Throwable e){
        if(e instanceof MCodeException m) return m.typeName+": "+m.detail;
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }

    private static final class Runtime {
        final Environment global=new Environment(null,true,false);
        final Path workspaceRoot;
        final Map<String,MModule> modules=new LinkedHashMap<>();
        final Set<String> importing=new HashSet<>();
        MCodeException currentException;
        MClass currentFunctionClass;
        Object currentSelf;
        List<Object> activeYieldCollector;

        Runtime(Path workspaceRoot){
            this.workspaceRoot=(workspaceRoot==null?MCodeProjectManager.getWorkspaceRoot():workspaceRoot).toAbsolutePath().normalize();
        }

        void installBuiltins(){
            installBuiltinsInto(global);
        }

        void installBuiltinsInto(Environment target){
            target.define("print",(Callable)this::builtinPrint);
            target.define("len",(Callable)this::builtinLen);
            target.define("type",(Callable)this::builtinType);
            target.define("int",(Callable)this::builtinInt);
            target.define("float",(Callable)this::builtinFloat);
            target.define("str",(Callable)this::builtinStr);
            target.define("bool",(Callable)this::builtinBool);
            target.define("list",(Callable)this::builtinList);
            target.define("tuple",(Callable)this::builtinTuple);
            target.define("set",(Callable)this::builtinSet);
            target.define("dict",(Callable)this::builtinDict);
            target.define("range",(Callable)this::builtinRange);
            target.define("abs",(Callable)this::builtinAbs);
            target.define("round",(Callable)this::builtinRound);
            target.define("min",(Callable)this::builtinMin);
            target.define("max",(Callable)this::builtinMax);
            target.define("sum",(Callable)this::builtinSum);
            target.define("any",(Callable)this::builtinAny);
            target.define("all",(Callable)this::builtinAll);
            target.define("enumerate",(Callable)this::builtinEnumerate);
            target.define("zip",(Callable)this::builtinZip);
            target.define("sorted",(Callable)this::builtinSorted);
            target.define("reversed",(Callable)this::builtinReversed);
            target.define("isinstance",(Callable)this::builtinIsInstance);
            target.define("issubclass",(Callable)this::builtinIsSubclass);
            target.define("repr",(Callable)this::builtinRepr);
            target.define("ascii",(Callable)this::builtinAscii);
            target.define("format",(Callable)this::builtinFormat);
            target.define("hex",(Callable)this::builtinHex);
            target.define("oct",(Callable)this::builtinOct);
            target.define("bin",(Callable)this::builtinBin);
            target.define("ord",(Callable)this::builtinOrd);
            target.define("chr",(Callable)this::builtinChr);
            target.define("hash",(Callable)this::builtinHash);
            target.define("id",(Callable)this::builtinId);
            target.define("callable",(Callable)this::builtinCallable);
            target.define("dir",(Callable)this::builtinDir);
            target.define("getattr",(Callable)this::builtinGetAttr);
            target.define("setattr",(Callable)this::builtinSetAttr);
            target.define("hasattr",(Callable)this::builtinHasAttr);
            target.define("delattr",(Callable)this::builtinDelAttr);
            target.define("vars",(Callable)this::builtinVars);
            target.define("object",OBJECT_CLASS);
            target.define("sleep",(Callable)this::builtinSleep);
            target.define("pow",(Callable)this::builtinPow);
            target.define("divmod",(Callable)this::builtinDivmod);
            target.define("iter",(Callable)this::builtinIter);
            target.define("next",(Callable)this::builtinNext);
            target.define("map",(Callable)this::builtinMap);
            target.define("filter",(Callable)this::builtinFilter);
            target.define("super",(Callable)this::builtinSuper);

            if(EXCEPTION_TYPES.isEmpty()){
                MExceptionType base=new MExceptionType("BaseException",null);
                MExceptionType exception=new MExceptionType("Exception",base);
                EXCEPTION_TYPES.put("BaseException",base);
                EXCEPTION_TYPES.put("Exception",exception);
                EXCEPTION_TYPES.put("RuntimeError",new MExceptionType("RuntimeError",exception));
                EXCEPTION_TYPES.put("ValueError",new MExceptionType("ValueError",exception));
                EXCEPTION_TYPES.put("TypeError",new MExceptionType("TypeError",exception));
                EXCEPTION_TYPES.put("NameError",new MExceptionType("NameError",exception));
                EXCEPTION_TYPES.put("IndexError",new MExceptionType("IndexError",exception));
                EXCEPTION_TYPES.put("KeyError",new MExceptionType("KeyError",exception));
                EXCEPTION_TYPES.put("ZeroDivisionError",new MExceptionType("ZeroDivisionError",EXCEPTION_TYPES.get("ArithmeticError")));
            }
            if(!EXCEPTION_TYPES.containsKey("ArithmeticError")) EXCEPTION_TYPES.put("ArithmeticError",new MExceptionType("ArithmeticError",EXCEPTION_TYPES.get("Exception")));
            if(EXCEPTION_TYPES.get("ZeroDivisionError") instanceof MExceptionType z && z.base==null) EXCEPTION_TYPES.put("ZeroDivisionError",new MExceptionType("ZeroDivisionError",EXCEPTION_TYPES.get("ArithmeticError")));
            if(!EXCEPTION_TYPES.containsKey("AttributeError")) EXCEPTION_TYPES.put("AttributeError",new MExceptionType("AttributeError",EXCEPTION_TYPES.get("Exception")));
            if(!EXCEPTION_TYPES.containsKey("SyntaxError")) EXCEPTION_TYPES.put("SyntaxError",new MExceptionType("SyntaxError",EXCEPTION_TYPES.get("Exception")));
            if(!EXCEPTION_TYPES.containsKey("ImportError")) EXCEPTION_TYPES.put("ImportError",new MExceptionType("ImportError",EXCEPTION_TYPES.get("Exception")));
            if(!EXCEPTION_TYPES.containsKey("ModuleNotFoundError")) EXCEPTION_TYPES.put("ModuleNotFoundError",new MExceptionType("ModuleNotFoundError",EXCEPTION_TYPES.get("ImportError")));
            for(var e:EXCEPTION_TYPES.entrySet()) target.define(e.getKey(),e.getValue());
        }

        void executeProgram(Program p){
            for(Statement s:p.statements()) execute(s,global);
        }

        void execute(Statement s,Environment env){
            if(s instanceof ExpressionStatement x){ eval(x.expression(),env); return; }
            if(s instanceof Assignment x){ Object v=eval(x.value(),env); for(Expression t:x.targets()) assign(t,v,env); return; }
            if(s instanceof AugmentedAssignment x){ Object old=eval(x.target(),env); Object v=eval(x.value(),env); assign(x.target(),binary(old,x.operator(),v),env); return; }

            if(s instanceof If x){
                for(IfBranch b:x.branches()) if(truthy(eval(b.condition(),env))){ executeBlock(b.body(),env); return; }
                if(x.elseBlock()!=null) executeBlock(x.elseBlock(),env);
                return;
            }

            if(s instanceof While x){ executeWhile(x,env); return; }
            if(s instanceof For x){ executeFor(x,env); return; }

            if(s instanceof FunctionDef x){
                Map<String,Object> defaults=new LinkedHashMap<>();
                for(Parameter p:x.parameters()) if(p.defaultValue()!=null) defaults.put(p.name(),eval(p.defaultValue(),env));
                Environment closure=env.classScope?env.parent:env;
                env.assignLocal(x.name(),new MFunction(x.name(),x.parameters(),defaults,x.body(),null,closure,containsYield(x.body())));
                return;
            }

            if(s instanceof AsyncFunctionDef x){
                Map<String,Object> defaults=new LinkedHashMap<>();
                for(Parameter p:x.parameters()) if(p.defaultValue()!=null) defaults.put(p.name(),eval(p.defaultValue(),env));
                Environment closure=env.classScope?env.parent:env;
                MFunction delegate=new MFunction(x.name(),x.parameters(),defaults,x.body(),null,closure,false);
                env.assignLocal(x.name(),new MAsyncFunction(delegate));
                return;
            }

            if(s instanceof Import x){ executeImport(x,env); return; }
            if(s instanceof FromImport x){ executeFromImport(x,env); return; }
            if(s instanceof Match x){ executeMatch(x,env); return; }
            if(s instanceof With x){ executeWith(x,env); return; }

            if(s instanceof ClassDef x){ executeClassDef(x,env); return; }
            if(s instanceof Return x){
                if(!env.inFunction) throw runtimeError("SyntaxError: 'return' fuera de una función");
                throw new ReturnSignal(x.value()==null?null:eval(x.value(),env));
            }
            if(s instanceof Break){
                if(env.loopDepth<=0) throw runtimeError("SyntaxError: 'break' fuera de un bucle");
                throw new BreakSignal();
            }
            if(s instanceof Continue){
                if(env.loopDepth<=0) throw runtimeError("SyntaxError: 'continue' fuera de un bucle");
                throw new ContinueSignal();
            }
            if(s instanceof Pass) return;

            if(s instanceof Try x){ executeTry(x,env); return; }
            if(s instanceof Raise x){
                if(x.value()==null){
                    if(currentException==null) throw runtimeError("RuntimeError: no hay una excepción activa");
                    throw currentException;
                }
                Object v=eval(x.value(),env);
                throw exceptionFrom(v);
            }
            if(s instanceof Global x){ for(String n:x.names()) env.declareGlobal(n); return; }
            if(s instanceof Nonlocal x){ for(String n:x.names()) env.declareNonlocal(n); return; }

            throw runtimeError("Sentencia no soportada: "+s.getClass().getSimpleName());
        }

        private void executeImport(Import x,Environment env){
            for(ImportItem item:x.items()){
                MModule module=importModule(item.name());
                String binding=item.alias()!=null?item.alias():item.name().substring(item.name().lastIndexOf('.')+1);
                env.assign(binding,module);
            }
        }

        private void executeFromImport(FromImport x,Environment env){
            MModule module=importModule(x.module());
            if(x.star()){
                for(var entry:module.attrs.entrySet()) if(!entry.getKey().startsWith("_")) env.assign(entry.getKey(),entry.getValue());
                return;
            }
            for(ImportItem item:x.items()){
                if(!module.attrs.containsKey(item.name())) throw runtimeError("ImportError: no existe '"+item.name()+"' en el módulo '"+x.module()+"'");
                env.assign(item.alias()!=null?item.alias():item.name(),module.attrs.get(item.name()));
            }
        }

        private MModule importModule(String moduleName){
            MModule cached=modules.get(moduleName);
            if(cached!=null) return cached;
            if(moduleName.equals("time")){ MModule m=builtinTimeModule(); modules.put(moduleName,m); return m; }
            if(moduleName.equals("math")){ MModule m=builtinMathModule(); modules.put(moduleName,m); return m; }
            if(!importing.add(moduleName)) throw runtimeError("ImportError: importación circular: "+moduleName);
            try{
                Path file=resolveModule(moduleName);
                if(file==null) throw runtimeError("ModuleNotFoundError: no se encontró el módulo '"+moduleName+"'");
                String source=Files.readString(file,StandardCharsets.UTF_8);
                Program program=new MCodeParser(new MCodeLexer().tokenize(source)).parse();
                Environment moduleEnv=new Environment(global,false,false);
                MModule module=new MModule(moduleName,moduleEnv.values);
                modules.put(moduleName,module);
                for(Statement statement:program.statements()) execute(statement,moduleEnv);
                module.attrs=moduleEnv.snapshot();
                return module;
            }catch(java.io.IOException e){
                throw runtimeError("ImportError: no se pudo leer '"+moduleName+"': "+e.getMessage());
            }finally{
                importing.remove(moduleName);
            }
        }

        private Path resolveModule(String moduleName){
            String clean=moduleName.replace('.', '/');
            Path file=workspaceRoot.resolve(clean+".mcode").normalize();
            if(file.startsWith(workspaceRoot)&&Files.isRegularFile(file)) return file;
            Path init=workspaceRoot.resolve(clean).resolve("__init__.mcode").normalize();
            if(init.startsWith(workspaceRoot)&&Files.isRegularFile(init)) return init;
            return null;
        }

        private void executeMatch(Match x,Environment env){
            Object subject=eval(x.subject(),env);
            for(MatchCase c:x.cases()){
                Map<String,Object> pending=new LinkedHashMap<>();
                if(matchPattern(subject,c.pattern(),env,pending)){
                    boolean guard=c.guard()==null||truthy(evalWithBindings(c.guard(),env,pending));
                    if(guard){
                        for(var e:pending.entrySet()) env.assign(e.getKey(),e.getValue());
                        executeBlock(c.body(),env);
                        return;
                    }
                }
            }
        }

        private Object evalWithBindings(Expression e,Environment env,Map<String,Object> bindings){
            Environment local=new Environment(env,false,false);
            for(var entry:bindings.entrySet()) local.define(entry.getKey(),entry.getValue());
            return eval(e,local);
        }

        private boolean matchPattern(Object subject,Expression pattern,Environment env,Map<String,Object> pending){
            if(pattern instanceof Name n){
                if(n.name().equals("_")) return true;
                pending.put(n.name(),subject);
                return true;
            }
            if(pattern instanceof Literal l) return Objects.equals(subject,l.value());
            if(pattern instanceof Binary b && b.operator().equals("|")){
                Map<String,Object> left=new LinkedHashMap<>(pending);
                if(matchPattern(subject,b.left(),env,left)){ pending.clear(); pending.putAll(left); return true; }
                Map<String,Object> right=new LinkedHashMap<>(pending);
                if(matchPattern(subject,b.right(),env,right)){ pending.clear(); pending.putAll(right); return true; }
                return false;
            }
            if(pattern instanceof TupleExpr t){
                if(!(subject instanceof TupleValue tuple)||tuple.values.size()!=t.elements().size()) return false;
                for(int i=0;i<t.elements().size();i++) if(!matchPattern(tuple.values.get(i),t.elements().get(i),env,pending)) return false;
                return true;
            }
            if(pattern instanceof ListExpr l){
                List<Object> values=iterable(subject);
                if(values.size()!=l.elements().size()) return false;
                for(int i=0;i<l.elements().size();i++) if(!matchPattern(values.get(i),l.elements().get(i),env,pending)) return false;
                return true;
            }
            return Objects.equals(subject,eval(pattern,env));
        }

        private void executeWith(With x,Environment env){
            executeWithIndex(x.items(),0,x.body(),env);
        }

        private void executeWithIndex(List<WithItem> items,int index,Block body,Environment env){
            if(index>=items.size()){ executeBlock(body,env); return; }
            WithItem item=items.get(index);
            Object context=eval(item.context(),env);
            Object enter= getAttribute(context,"__enter__");
            if(!(enter instanceof Callable enterCall)) throw runtimeError("AttributeError: objeto de tipo '"+typeName(context)+"' no tiene __enter__()");
            Object entered=enterCall.call(List.of(),Map.of());
            if(item.asName()!=null) env.assign(item.asName(),entered);
            Object exit=getAttribute(context,"__exit__");
            if(!(exit instanceof Callable exitCall)) throw runtimeError("AttributeError: objeto de tipo '"+typeName(context)+"' no tiene __exit__()");
            try{
                executeWithIndex(items,index+1,body,env);
                exitCall.call(Arrays.asList(null,null,null),Map.of());
            }catch(MCodeException ex){
                Object suppress=exitCall.call(Arrays.asList(ex.typeName,ex,null),Map.of());
                if(!truthy(suppress)) throw ex;
            }catch(RuntimeException ex){
                throw ex;
            }
        }

        private void executeWhile(While x,Environment env){
            boolean broke=false;
            env.loopDepth++;
            try{
                while(truthy(eval(x.condition(),env))){
                    try{ executeBlock(x.body(),env); }
                    catch(ContinueSignal c){ continue; }
                    catch(BreakSignal b){ broke=true; break; }
                }
            }finally{ env.loopDepth--; }
            if(!broke&&x.elseBlock()!=null) executeBlock(x.elseBlock(),env);
        }

        private void executeFor(For x,Environment env){
            boolean broke=false;
            List<Object> values=iterable(eval(x.iterable(),env));
            env.loopDepth++;
            try{
                outer: for(Object value:values){
                    try{
                        assign(x.target(),value,env);
                        executeBlock(x.body(),env);
                    }catch(ContinueSignal c){ continue; }
                    catch(BreakSignal b){ broke=true; break outer; }
                }
            }finally{ env.loopDepth--; }
            if(!broke&&x.elseBlock()!=null) executeBlock(x.elseBlock(),env);
        }

        private void executeClassDef(ClassDef x,Environment env){
            List<MClass> bases=new ArrayList<>();
            for(Expression e:x.bases()){
                Object v=eval(e,env);
                if(!(v instanceof MClass c)) throw runtimeError("TypeError: las clases base deben ser clases");
                bases.add(c);
            }
            Environment classEnv=new Environment(env,false,true);
            for(Statement s:x.body().statements()) execute(s,classEnv);
            MClass clazz=new MClass(x.name(),List.copyOf(bases),classEnv.snapshot());
            for(Object value:clazz.attrs.values()) if(value instanceof MFunction f) f.setDefiningClass(clazz);
            env.assignLocal(x.name(),clazz);
        }

        private void executeTry(Try x,Environment env){
            boolean completed=false;
            try{
                try{
                    executeBlock(x.body(),env);
                    completed=true;
                }catch(MCodeException ex){
                    boolean handled=false;
                    for(ExceptHandler h:x.handlers()){
                        if(h.type()==null||exceptionMatches(ex,eval(h.type(),env))){
                            MCodeException previous=currentException;
                            currentException=ex;
                            try{
                                if(h.name()!=null) env.assign(h.name(),ex);
                                executeBlock(h.body(),env);
                            }finally{
                                currentException=previous;
                            }
                            handled=true;
                            break;
                        }
                    }
                    if(!handled) throw ex;
                }
                if(completed&&x.elseBlock()!=null) executeBlock(x.elseBlock(),env);
            }finally{
                if(x.finallyBlock()!=null) executeBlock(x.finallyBlock(),env);
            }
        }

        private boolean exceptionMatches(MCodeException ex,Object type){
            if(type instanceof MExceptionType t){
                MExceptionType actual=EXCEPTION_TYPES.get(ex.typeName);
                if(actual==null) return t.name.equals(ex.typeName);
                for(MExceptionType cur=actual;cur!=null;cur=cur.base){
                    if(cur.name.equals(t.name)) return true;
                }
                return false;
            }
            if(type instanceof MClass c) return c.isException && c.isSubclassNamed(ex.typeName);
            return false;
        }

        Object eval(Expression e,Environment env){
            if(e instanceof Literal x) return x.value();
            if(e instanceof Name x) return env.lookup(x.name());
            if(e instanceof Attribute x) return getAttribute(eval(x.target(),env),x.name());
            if(e instanceof ListExpr x){ List<Object> out=new ArrayList<>(); for(Expression a:x.elements()) out.add(eval(a,env)); return out; }
            if(e instanceof TupleExpr x){ List<Object> out=new ArrayList<>(); for(Expression a:x.elements()) out.add(eval(a,env)); return new TupleValue(out); }
            if(e instanceof SetExpr x){ Set<Object> out=new LinkedHashSet<>(); for(Expression a:x.elements()) out.add(eval(a,env)); return out; }
            if(e instanceof DictExpr x){ Map<Object,Object> out=new LinkedHashMap<>(); for(Entry a:x.entries()) out.put(eval(a.key(),env),eval(a.value(),env)); return out; }
            if(e instanceof Unary x) return unary(x.operator(),eval(x.expression(),env));
            if(e instanceof Binary x){
                if(x.operator().equals("and")){
                    Object l=eval(x.left(),env); return truthy(l)?eval(x.right(),env):l;
                }
                if(x.operator().equals("or")){
                    Object l=eval(x.left(),env); return truthy(l)?l:eval(x.right(),env);
                }
                return binary(eval(x.left(),env),x.operator(),eval(x.right(),env));
            }
            if(e instanceof Comparison x){
                Object left=eval(x.left(),env);
                for(int i=0;i<x.operators().size();i++){
                    Object right=eval(x.rights().get(i),env);
                    if(!comparison(left,x.operators().get(i),right)) return false;
                    left=right;
                }
                return true;
            }
            if(e instanceof Call x) return call(eval(x.callee(),env),x.arguments(),env);
            if(e instanceof Subscript x){
                Object target=eval(x.target(),env);
                if(x.subscript() instanceof Slice s) return readSlice(target,s,env);
                return readSubscript(target,eval(x.subscript(),env));
            }
            if(e instanceof Slice) throw runtimeError("SyntaxError: una slice debe estar dentro de un índice");
            if(e instanceof Lambda x){
                Map<String,Object> defaults=new LinkedHashMap<>();
                for(Parameter p:x.parameters()) if(p.defaultValue()!=null) defaults.put(p.name(),eval(p.defaultValue(),env));
                return new MFunction("<lambda>",x.parameters(),defaults,null,x.body(),env,false);
            }
            if(e instanceof Await x) return awaitValue(eval(x.expression(),env));
            if(e instanceof Yield x){
                if(activeYieldCollector==null) throw runtimeError("SyntaxError: 'yield' fuera de un generador");
                if(x.from()){ for(Object value:iterable(eval(x.expression(),env))) activeYieldCollector.add(value); return null; }
                Object value=x.expression()==null?null:eval(x.expression(),env);
                activeYieldCollector.add(value);
                return value;
            }
            if(e instanceof ListComp x) return comprehensionList(x.element(),x.generators(),env);
            if(e instanceof SetComp x) return comprehensionSet(x.element(),x.generators(),env);
            if(e instanceof DictComp x) return comprehensionDict(x.key(),x.value(),x.generators(),env);
            throw runtimeError("Expresión no soportada: "+e.getClass().getSimpleName());
        }

        private Object awaitValue(Object value){
            if(value instanceof MCoroutine c) return c.await();
            return value;
        }

        private MGenerator collectGenerator(MFunction function,Environment env){
            List<Object> previous=activeYieldCollector;
            List<Object> values=new ArrayList<>();
            activeYieldCollector=values;
            try{
                if(function.body!=null) for(Statement s:function.body.statements()) execute(s,env);
            }catch(ReturnSignal ignored){
            }finally{
                activeYieldCollector=previous;
            }
            return new MGenerator(values);
        }

        private void executeBlock(Block b,Environment env){ for(Statement s:b.statements()) execute(s,env); }

        private Object call(Object callable,List<CallArg> args,Environment env){
            if(!(callable instanceof Callable c)) throw runtimeError("TypeError: '"+typeName(callable)+"' no es invocable");
            List<Object> positional=new ArrayList<>();
            Map<String,Object> keywords=new LinkedHashMap<>();
            boolean keywordSeen=false;
            for(CallArg a:args){
                Object value=eval(a.value(),env);
                if(a.keywordUnpack()){
                    if(!(value instanceof Map<?,?> m)) throw runtimeError("TypeError: ** requiere un diccionario");
                    keywordSeen=true;
                    for(Map.Entry<?,?> e:m.entrySet()) keywords.put(String.valueOf(e.getKey()),e.getValue());
                }else if(a.unpack()){
                    if(keywordSeen) throw runtimeError("SyntaxError: argumento posicional después de keyword");
                    positional.addAll(iterable(value));
                }else if(a.name()!=null){
                    if(keywords.containsKey(a.name())) throw runtimeError("TypeError: argumento keyword duplicado: "+a.name());
                    keywordSeen=true; keywords.put(a.name(),value);
                }else{
                    if(keywordSeen) throw runtimeError("SyntaxError: argumento posicional después de keyword");
                    positional.add(value);
                }
            }
            return c.call(positional,keywords);
        }

        private void assign(Expression target,Object value,Environment env){
            if(target instanceof Name n){ env.assign(n.name(),value); return; }
            if(target instanceof Attribute a){ setAttribute(eval(a.target(),env),a.name(),value); return; }
            if(target instanceof Subscript s){ writeSubscript(eval(s.target(),env),eval(s.subscript(),env),value); return; }
            if(target instanceof TupleExpr || target instanceof ListExpr){
                List<Expression> xs=target instanceof TupleExpr
                        ?((TupleExpr)target).elements()
                        :((ListExpr)target).elements();
                List<Object> vs=iterable(value);
                if(xs.size()!=vs.size()) throw runtimeError("ValueError: demasiados o muy pocos valores para desempaquetar");
                for(int i=0;i<xs.size();i++) assign(xs.get(i),vs.get(i),env);
                return;
            }
            throw runtimeError("SyntaxError: objetivo de asignación inválido");
        }

        private List<Object> comprehensionValues(List<Comprehension> gs,Environment parent,CompConsumer consumer){
            Environment local=new Environment(parent,false,false);
            consumer.run(local,gs,0);
            return consumer.values();
        }

        private List<Object> comprehensionList(Expression element,List<Comprehension> gs,Environment env){
            List<Object> out=new ArrayList<>();
            runComprehension(gs,0,new Environment(env,false,false),local->out.add(eval(element,local)));
            return out;
        }

        private Set<Object> comprehensionSet(Expression element,List<Comprehension> gs,Environment env){
            Set<Object> out=new LinkedHashSet<>();
            runComprehension(gs,0,new Environment(env,false,false),local->out.add(eval(element,local)));
            return out;
        }

        private Map<Object,Object> comprehensionDict(Expression key,Expression value,List<Comprehension> gs,Environment env){
            Map<Object,Object> out=new LinkedHashMap<>();
            runComprehension(gs,0,new Environment(env,false,false),local->out.put(eval(key,local),eval(value,local)));
            return out;
        }

        private void runComprehension(List<Comprehension> gs,int index,Environment local,java.util.function.Consumer<Environment> emit){
            if(index>=gs.size()){ emit.accept(local); return; }
            Comprehension g=gs.get(index);
            List<Object> values=iterable(eval(g.iterable(),local));
            for(Object value:values){
                Environment next=new Environment(local,false,false);
                assign(g.target(),value,next);
                boolean ok=true;
                for(Expression c:g.conditions()) if(!truthy(eval(c,next))){ok=false;break;}
                if(ok) runComprehension(gs,index+1,next,emit);
            }
        }

        private Object builtinPrint(List<Object> a,Map<String,Object> k){
            String sep=k.getOrDefault("sep"," ") instanceof String s?s:" ";
            String end=k.getOrDefault("end","\n") instanceof String s?s:"\n";
            for(int i=0;i<a.size();i++){ if(i>0) OUTPUT.append(sep); OUTPUT.append(stringify(a.get(i))); }
            OUTPUT.append(end); return null;
        }
        private Object builtinLen(List<Object>a,Map<String,Object>k){requireCount("len",a,1);return BigInteger.valueOf(length(a.get(0)));}
        private Object builtinType(List<Object>a,Map<String,Object>k){requireCount("type",a,1);return typeName(a.get(0));}
        private Object builtinInt(List<Object>a,Map<String,Object>k){if(a.isEmpty()||a.size()>2) throw runtimeError("TypeError: int() arguments"); Object v=a.get(0); if(v instanceof BigInteger)return v; if(v instanceof Number)return BigInteger.valueOf(((Number)v).longValue()); if(v instanceof String){try{return new BigInteger((String)v);}catch(Exception e){throw runtimeError("ValueError: invalid literal for int()") ;}} throw runtimeError("TypeError: int() no puede convertir "+typeName(v));}
        private Object builtinFloat(List<Object>a,Map<String,Object>k){requireCount("float",a,1); Object v=a.get(0); if(v instanceof Number)return ((Number)v).doubleValue(); if(v instanceof String){try{return Double.parseDouble((String)v);}catch(Exception e){throw runtimeError("ValueError: could not convert string to float");}} throw runtimeError("TypeError: float() no puede convertir "+typeName(v));}
        private Object builtinStr(List<Object>a,Map<String,Object>k){requireCount("str",a,1);return stringify(a.get(0));}
        private Object builtinBool(List<Object>a,Map<String,Object>k){requireCount("bool",a,1);return truthy(a.get(0));}
        private Object builtinList(List<Object>a,Map<String,Object>k){if(a.isEmpty())return new ArrayList<>();requireCount("list",a,1);return new ArrayList<>(iterable(a.get(0)));}
        private Object builtinTuple(List<Object>a,Map<String,Object>k){if(a.isEmpty())return new TupleValue(List.of());requireCount("tuple",a,1);return new TupleValue(iterable(a.get(0)));}
        private Object builtinSet(List<Object>a,Map<String,Object>k){if(a.isEmpty())return new LinkedHashSet<>();requireCount("set",a,1);return new LinkedHashSet<>(iterable(a.get(0)));}
        private Object builtinDict(List<Object>a,Map<String,Object>k){Map<Object,Object> out=new LinkedHashMap<>();if(a.size()>1)throw runtimeError("TypeError: dict() esperaba como máximo 1 argumento posicional");if(a.size()==1){if(!(a.get(0) instanceof Map<?,?> m))throw runtimeError("TypeError: dict() argumento no soportado");m.forEach(out::put);}for(var e:k.entrySet())out.put(e.getKey(),e.getValue());return out;}
        private Object builtinRange(List<Object>a,Map<String,Object>k){if(a.size()<1||a.size()>3)throw runtimeError("TypeError: range expected 1 to 3 arguments");int start,stop,step;if(a.size()==1){start=0;stop=toInt(a.get(0));step=1;}else{start=toInt(a.get(0));stop=toInt(a.get(1));step=a.size()==3?toInt(a.get(2)):1;}if(step==0)throw runtimeError("ValueError: range() arg 3 must not be zero");return new MRange(start,stop,step);}
        private Object builtinPow(List<Object>a,Map<String,Object>k){if(a.size()<2||a.size()>3)throw runtimeError("TypeError: pow() expected 2 or 3 arguments");Object base=a.get(0),exp=a.get(1);Object out=binary(base,"**",exp);if(a.size()==3){BigInteger mod=toBigInteger(a.get(2));if(out instanceof BigInteger i)return i.mod(mod);throw runtimeError("TypeError: pow() 3-argument form requires integers");}return out;}
        private Object builtinDivmod(List<Object>a,Map<String,Object>k){requireCount("divmod",a,2);Object q=binary(a.get(0),"//",a.get(1));Object r=binary(a.get(0),"%",a.get(1));return new TupleValue(List.of(q,r));}
        private Object builtinIter(List<Object>a,Map<String,Object>k){requireCount("iter",a,1);if(a.get(0) instanceof MIterator i)return i;return new MIterator(iterable(a.get(0)));}
        private Object builtinNext(List<Object>a,Map<String,Object>k){if(a.isEmpty()||a.size()>2)throw runtimeError("TypeError: next() expected 1 or 2 arguments");if(!(a.get(0) instanceof MIterator it))throw runtimeError("TypeError: object is not an iterator");try{return it.next();}catch(NoSuchElementException e){if(a.size()==2)return a.get(1);throw runtimeError("StopIteration");}}
        private Object builtinMap(List<Object>a,Map<String,Object>k){if(a.size()<2)throw runtimeError("TypeError: map() expected at least 2 arguments");if(!(a.get(0) instanceof Callable f))throw runtimeError("TypeError: map() argument 1 must be callable");List<List<Object>> seqs=new ArrayList<>();for(int i=1;i<a.size();i++)seqs.add(iterable(a.get(i)));int n=seqs.stream().mapToInt(List::size).min().orElse(0);List<Object>out=new ArrayList<>();for(int i=0;i<n;i++){List<Object> args=new ArrayList<>();for(List<Object> seq:seqs)args.add(seq.get(i));out.add(f.call(args,Map.of()));}return new MIterator(out);}
        private Object builtinFilter(List<Object>a,Map<String,Object>k){requireCount("filter",a,2);Callable f=null;if(a.get(0)!=null){if(!(a.get(0) instanceof Callable c))throw runtimeError("TypeError: filter() argument 1 must be callable");f=c;}List<Object>out=new ArrayList<>();for(Object x:iterable(a.get(1))){boolean keep=f==null?truthy(x):truthy(f.call(List.of(x),Map.of()));if(keep)out.add(x);}return new MIterator(out);}
        private Object builtinAbs(List<Object>a,Map<String,Object>k){requireCount("abs",a,1);Object v=a.get(0);if(v instanceof BigInteger i)return i.abs();if(v instanceof Number n)return Math.abs(n.doubleValue());throw runtimeError("TypeError: bad operand type for abs()");}
        private Object builtinRound(List<Object>a,Map<String,Object>k){requireCount("round",a,1);Object v=a.get(0);int nd=a.size()>1?toInt(a.get(1)):0;if(v instanceof BigInteger)return v;if(v instanceof Number)return BigDecimal.valueOf(((Number)v).doubleValue()).setScale(nd,RoundingMode.HALF_EVEN).doubleValue();throw runtimeError("TypeError: round() argument must be a number");}
        private Object builtinMin(List<Object>a,Map<String,Object>k){return extremum(a,true);}
        private Object builtinMax(List<Object>a,Map<String,Object>k){return extremum(a,false);}
        private Object extremum(List<Object>a,boolean min){List<Object> v=a.size()==1?iterable(a.get(0)):a;if(v.isEmpty())throw runtimeError("ValueError: min/max arg is an empty sequence");Object best=v.get(0);for(int i=1;i<v.size();i++){int c=compare(v.get(i),best);if((min&&c<0)||(!min&&c>0))best=v.get(i);}return best;}
        private Object builtinSum(List<Object>a,Map<String,Object>k){List<Object>v=a.isEmpty()?List.of():iterable(a.get(0));Object total=a.size()>1?a.get(1):BigInteger.ZERO;for(Object x:v)total=binary(total,"+",x);return total;}
        private Object builtinAny(List<Object>a,Map<String,Object>k){requireCount("any",a,1);for(Object x:iterable(a.get(0)))if(truthy(x))return true;return false;}
        private Object builtinAll(List<Object>a,Map<String,Object>k){requireCount("all",a,1);for(Object x:iterable(a.get(0)))if(!truthy(x))return false;return true;}
        private Object builtinEnumerate(List<Object>a,Map<String,Object>k){requireCount("enumerate",a,1);int start=a.size()>1?toInt(a.get(1)):0;List<Object>out=new ArrayList<>();for(Object x:iterable(a.get(0))){out.add(new TupleValue(List.of(BigInteger.valueOf(start++),x)));}return out;}
        private Object builtinZip(List<Object>a,Map<String,Object>k){List<List<Object>> all=new ArrayList<>();for(Object x:a)all.add(iterable(x));int n=all.stream().mapToInt(List::size).min().orElse(0);List<Object>out=new ArrayList<>();for(int i=0;i<n;i++){List<Object>row=new ArrayList<>();for(List<Object> v:all)row.add(v.get(i));out.add(new TupleValue(row));}return out;}
        private Object builtinSorted(List<Object>a,Map<String,Object>k){requireCount("sorted",a,1);List<Object>out=new ArrayList<>(iterable(a.get(0)));out.sort(MCodeInterpreter::compare);if(truthy(k.getOrDefault("reverse",false)))Collections.reverse(out);return out;}
        private Object builtinReversed(List<Object>a,Map<String,Object>k){requireCount("reversed",a,1);List<Object>out=new ArrayList<>(iterable(a.get(0)));Collections.reverse(out);return new MIterator(out);}
        private Object builtinIsInstance(List<Object>a,Map<String,Object>k){requireCount("isinstance",a,2);Object t=a.get(1),v=a.get(0);if(t instanceof MClass c)return isInstance(v,c);if(t instanceof MExceptionType et)return et.matches(exceptionTypeOf(v));return false;}
        private Object builtinIsSubclass(List<Object>a,Map<String,Object>k){requireCount("issubclass",a,2);if(!(a.get(0) instanceof MClass c))return false;if(a.get(1) instanceof MClass b)return c.isSubclassOf(b);return false;}
        private Object builtinRepr(List<Object>a,Map<String,Object>k){requireCount("repr",a,1);return stringify(a.get(0));}
        private Object builtinAscii(List<Object>a,Map<String,Object>k){requireCount("ascii",a,1);return asciiString(stringify(a.get(0)));}
        private Object builtinFormat(List<Object>a,Map<String,Object>k){if(a.isEmpty()||a.size()>2)throw runtimeError("TypeError: format() arguments");String value=stringify(a.get(0));String spec=a.size()==2?stringify(a.get(1)):"";if(spec.isEmpty())return value;try{if(a.get(0) instanceof Number)return String.format(Locale.ROOT,"%"+spec,((Number)a.get(0)).doubleValue());}catch(Exception ignored){}return value;}
        private Object builtinHex(List<Object>a,Map<String,Object>k){requireCount("hex",a,1);return "0x"+toBigInteger(a.get(0)).toString(16);}
        private Object builtinOct(List<Object>a,Map<String,Object>k){requireCount("oct",a,1);return "0o"+toBigInteger(a.get(0)).toString(8);}
        private Object builtinBin(List<Object>a,Map<String,Object>k){requireCount("bin",a,1);return "0b"+toBigInteger(a.get(0)).toString(2);}
        private Object builtinOrd(List<Object>a,Map<String,Object>k){requireCount("ord",a,1);String s=stringify(a.get(0));if(s.codePointCount(0,s.length())!=1)throw runtimeError("TypeError: ord() expected a character");return BigInteger.valueOf(s.codePointAt(0));}
        private Object builtinChr(List<Object>a,Map<String,Object>k){requireCount("chr",a,1);int n=toInt(a.get(0));if(!Character.isValidCodePoint(n))throw runtimeError("ValueError: chr() arg not in range");return new String(Character.toChars(n));}
        private Object builtinHash(List<Object>a,Map<String,Object>k){requireCount("hash",a,1);Object v=a.get(0);if(v instanceof List||v instanceof Map||v instanceof Set)throw runtimeError("TypeError: unhashable type: '"+typeName(v)+"'");return BigInteger.valueOf(Objects.hashCode(v));}
        private Object builtinId(List<Object>a,Map<String,Object>k){requireCount("id",a,1);return BigInteger.valueOf(System.identityHashCode(a.get(0)));}
        private Object builtinCallable(List<Object>a,Map<String,Object>k){requireCount("callable",a,1);return a.get(0) instanceof Callable;}
        private Object builtinDir(List<Object>a,Map<String,Object>k){if(a.isEmpty())return new ArrayList<>(global.values.keySet());requireCount("dir",a,1);return new ArrayList<>(getCompletionMembers(a.get(0)));}
        private Object builtinGetAttr(List<Object>a,Map<String,Object>k){if(a.size()<2||a.size()>3)throw runtimeError("TypeError: getattr() arguments");try{return getAttribute(a.get(0),stringify(a.get(1)));}catch(MCodeException e){if(a.size()==3)return a.get(2);throw e;}}
        private Object builtinSetAttr(List<Object>a,Map<String,Object>k){requireCount("setattr",a,3);setAttribute(a.get(0),stringify(a.get(1)),a.get(2));return null;}
        private Object builtinHasAttr(List<Object>a,Map<String,Object>k){requireCount("hasattr",a,2);try{getAttribute(a.get(0),stringify(a.get(1)));return true;}catch(MCodeException e){return false;}}
        private Object builtinDelAttr(List<Object>a,Map<String,Object>k){requireCount("delattr",a,2);deleteAttribute(a.get(0),stringify(a.get(1)));return null;}
        private Object builtinVars(List<Object>a,Map<String,Object>k){if(a.isEmpty())return new LinkedHashMap<>(global.values);requireCount("vars",a,1);Object value=a.get(0);if(value instanceof MInstance i)return new LinkedHashMap<>(i.fields);if(value instanceof MModule m)return new LinkedHashMap<>(m.attrs);if(value instanceof MClass c)return new LinkedHashMap<>(c.attrs);throw runtimeError("TypeError: vars() argument must have __dict__");}
        private Object builtinSleep(List<Object>a,Map<String,Object>k){requireCount("sleep",a,1);double seconds=toDouble(a.get(0));if(seconds<0)throw runtimeError("ValueError: sleep length must be non-negative");try{Thread.sleep((long)(seconds*1000), (int)((seconds*1_000_000_000L)%1_000_000));}catch(InterruptedException e){Thread.currentThread().interrupt();throw runtimeError("RuntimeError: sleep() fue interrumpido");}return null;}

        private MModule builtinTimeModule(){
            Map<String,Object> attrs=new LinkedHashMap<>();
            attrs.put("sleep",(Callable)this::builtinSleep);
            attrs.put("time",(Callable)(a,k)->BigDecimal.valueOf(System.currentTimeMillis()/1000.0));
            return new MModule("time",attrs);
        }

        private MModule builtinMathModule(){
            Map<String,Object> attrs=new LinkedHashMap<>();
            attrs.put("pi",Math.PI); attrs.put("e",Math.E);
            attrs.put("sqrt",(Callable)(a,k)->Math.sqrt(toDouble(first(a,"sqrt",1))));
            attrs.put("sin",(Callable)(a,k)->Math.sin(toDouble(first(a,"sin",1))));
            attrs.put("cos",(Callable)(a,k)->Math.cos(toDouble(first(a,"cos",1))));
            attrs.put("tan",(Callable)(a,k)->Math.tan(toDouble(first(a,"tan",1))));
            attrs.put("floor",(Callable)(a,k)->Math.floor(toDouble(first(a,"floor",1))));
            attrs.put("ceil",(Callable)(a,k)->Math.ceil(toDouble(first(a,"ceil",1))));
            attrs.put("log",(Callable)(a,k)->Math.log(toDouble(first(a,"log",1))));
            attrs.put("exp",(Callable)(a,k)->Math.exp(toDouble(first(a,"exp",1))));
            return new MModule("math",attrs);
        }

        private Object first(List<Object>a,String name,int n){requireCount(name,a,n);return a.get(0);}

        private Object builtinSuper(List<Object>a,Map<String,Object>k){
            MClass current=currentFunctionClass;
            Object self=currentSelf;
            if(a.size()==2&&a.get(0) instanceof MClass c){ current=c; self=a.get(1); }
            if(current==null||self==null) throw runtimeError("RuntimeError: super() no pudo determinar la clase actual");
            return new MSuper(current,self);
        }

        Map<String,Object> snapshot(){return global.snapshot();}
    }

    private interface CompConsumer {
        void run(Environment env,List<Comprehension> g,int i);
        default List<Object> values(){return List.of();}
    }

    private static final class Environment {
        final Environment parent;
        final boolean isGlobal;
        final boolean classScope;
        boolean inFunction;
        int loopDepth;
        final Map<String,Object> values=new LinkedHashMap<>();
        final Set<String> globals=new HashSet<>();
        final Set<String> nonlocals=new HashSet<>();

        Environment(Environment parent,boolean global,boolean classScope){
            this.parent=parent;this.isGlobal=global;this.classScope=classScope;
            this.inFunction=!global&&!classScope;
        }
        void define(String n,Object v){values.put(n,v);}
        void assignLocal(String n,Object v){values.put(n,v);}
        void declareGlobal(String n){globals.add(n);}
        void declareNonlocal(String n){nonlocals.add(n);}
        Object lookup(String n){
            if(values.containsKey(n))return values.get(n);
            if(parent!=null)return parent.lookup(n);
            throw runtimeError("NameError: name '"+n+"' is not defined");
        }
        void assign(String n,Object v){
            if(globals.contains(n)){root().values.put(n,v);return;}
            if(nonlocals.contains(n)){
                Environment e=findOuterLocal(n);
                if(e==null)throw runtimeError("SyntaxError: no binding for nonlocal '"+n+"'");
                e.values.put(n,v);return;
            }
            values.put(n,v);
        }
        Environment root(){Environment e=this;while(e.parent!=null)e=e.parent;return e;}
        Environment findOuterLocal(String n){for(Environment e=parent;e!=null&&!e.isGlobal;e=e.parent)if(e.values.containsKey(n))return e;return null;}
        Map<String,Object> snapshot(){return new LinkedHashMap<>(values);}
    }

    private interface Callable { Object call(List<Object> positional,Map<String,Object> keywords); }

    private static final class MFunction implements Callable {
        final String name; final List<Parameter> parameters; final Map<String,Object> defaults;
        final Block body; final Expression expressionBody; final Environment closure; final Object boundThis; final boolean generator; MClass definingClass;
        MFunction(String name,List<Parameter> p,Map<String,Object>d,Block b,Expression eb,Environment c,boolean generator){this(name,p,d,b,eb,c,null,generator);}
        MFunction(String name,List<Parameter> p,Map<String,Object>d,Block b,Expression eb,Environment c,Object bound,boolean generator){this.name=name;this.parameters=p;this.defaults=d;this.body=b;this.expressionBody=eb;this.closure=c;this.boundThis=bound;this.generator=generator;}
        MFunction bind(Object self){ MFunction f=new MFunction(name,parameters,defaults,body,expressionBody,closure,self,generator); f.definingClass=definingClass; return f; }
        void setDefiningClass(MClass c){definingClass=c;}
        public Object call(List<Object> positional,Map<String,Object> keywords){
            Environment env=new Environment(closure,false,false);
            List<Object> pos=new ArrayList<>(positional);
            Map<String,Object> kw=new LinkedHashMap<>(keywords);
            if(boundThis!=null)pos.add(0,boundThis);
            int index=0;
            for(Parameter p:parameters){
                if(p.varargs()){
                    env.define(p.name(),new ArrayList<>(pos.subList(index,pos.size()))); index=pos.size(); continue;
                }
                if(p.kwargs()){
                    env.define(p.name(),new LinkedHashMap<>(kw)); kw.clear(); continue;
                }
                if(index<pos.size()){
                    if(kw.containsKey(p.name()))throw runtimeError("TypeError: multiple values for argument '"+p.name()+"'");
                    env.define(p.name(),pos.get(index++));
                }else if(kw.containsKey(p.name()))env.define(p.name(),kw.remove(p.name()));
                else if(defaults.containsKey(p.name()))env.define(p.name(),defaults.get(p.name()));
                else throw runtimeError("TypeError: missing required argument '"+p.name()+"'");
            }
            if(index<pos.size())throw runtimeError("TypeError: too many positional arguments");
            if(!kw.isEmpty())throw runtimeError("TypeError: unexpected keyword argument '"+kw.keySet().iterator().next()+"'");
            Runtime rt=ACTIVE.get();
            if(generator){
                return rt.collectGenerator(this,env);
            }
            if(rt==null)throw runtimeError("Runtime no disponible");
            MClass oldClass=rt.currentFunctionClass;
            Object oldSelf=rt.currentSelf;
            rt.currentFunctionClass=definingClass;
            rt.currentSelf=boundThis;
            try{
                if(body!=null){for(Statement s:body.statements())rt.execute(s,env);return null;}
                return rt.eval(expressionBody,env);
            }catch(ReturnSignal r){return r.value;}
            finally{
                rt.currentFunctionClass=oldClass;
                rt.currentSelf=oldSelf;
            }
        }
    }

    private static final ThreadLocal<Runtime> ACTIVE=new ThreadLocal<>();

    private static final class MAsyncFunction implements Callable {
        final MFunction delegate;
        MAsyncFunction(MFunction delegate){this.delegate=delegate;}
        MAsyncFunction bind(Object self){return new MAsyncFunction(delegate.bind(self));}
        public Object call(List<Object> positional,Map<String,Object> keywords){
            return new MCoroutine(delegate,new ArrayList<>(positional),new LinkedHashMap<>(keywords));
        }
    }

    private static final class MCoroutine {
        final MFunction function;
        final List<Object> positional;
        final Map<String,Object> keywords;
        boolean completed;
        Object value;
        MCoroutine(MFunction function,List<Object> positional,Map<String,Object> keywords){this.function=function;this.positional=positional;this.keywords=keywords;}
        Object await(){
            if(!completed){ value=function.call(positional,keywords); completed=true; }
            return value;
        }
    }

    private static final class MGenerator {
        final List<Object> values;
        MGenerator(List<Object> values){this.values=List.copyOf(values);}
    }

    private static final class MModule {
        final String name;
        Map<String,Object> attrs;
        MModule(String name,Map<String,Object> attrs){this.name=name;this.attrs=new LinkedHashMap<>(attrs);}
    }

    private static final class MClass implements Callable {
        final String name; final List<MClass> bases; final Map<String,Object> attrs; final boolean isException;
        MClass(String name,List<MClass>bases,Map<String,Object>attrs){this(name,bases,attrs,false);}
        MClass(String name,List<MClass>bases,Map<String,Object>attrs,boolean exception){this.name=name;this.bases=bases;this.attrs= new LinkedHashMap<>(attrs);this.isException=exception;}
        Object lookup(String n){if(attrs.containsKey(n))return attrs.get(n);for(MClass b:bases){Object v=b.lookup(n);if(v!=null)return v;}return null;}
        public Object call(List<Object>pos,Map<String,Object>kw){
            MInstance instance=new MInstance(this);
            Object init=lookup("__init__");
            if(init instanceof MFunction f) f.bind(instance).call(pos,kw);
            else if(!pos.isEmpty()||!kw.isEmpty())throw runtimeError("TypeError: "+name+"() no acepta argumentos");
            return instance;
        }
        boolean isSubclassOf(MClass other){if(this==other)return true;for(MClass b:bases)if(b.isSubclassOf(other))return true;return false;}
        boolean isSubclassNamed(String type){return name.equals(type)||bases.stream().anyMatch(b->b.isSubclassNamed(type));}
    }

    private static final class MInstance {
        final MClass clazz; final Map<String,Object> fields=new LinkedHashMap<>();
        MInstance(MClass c){clazz=c;}
    }

    private static final class MExceptionType implements Callable {
        final String name; final MExceptionType base;
        MExceptionType(String name,MExceptionType base){this.name=name;this.base=base;}
        boolean matches(String type){for(MExceptionType t=this;t!=null;t=t.base)if(t.name.equals(type))return true;return false;}
        public Object call(List<Object>pos,Map<String,Object>kw){return new MCodeException(name,pos.isEmpty()?null:stringify(pos.get(0)));}
        public String toString(){return name;}
    }

    private static final class MCodeException extends RuntimeException {
        final String typeName; final String detail;
        MCodeException(String type,Object value){super(type);typeName=type;detail=value==null?"":stringify(value);}
    }

    private static final class MSuper {
        final MClass currentClass;
        final Object self;
        MSuper(MClass c,Object s){currentClass=c;self=s;}
        Object lookup(String name){
            return lookupBases(currentClass.bases,name);
        }
        private Object lookupBases(List<MClass> bases,String name){
            for(MClass base:bases){
                Object value=base.lookup(name);
                if(value!=null){
                    if(value instanceof MFunction f)return f.bind(self);
                    if(value instanceof MAsyncFunction f)return f.bind(self);
                    return value;
                }
            }
            return null;
        }
    }

    private static final class MIterator implements Iterable<Object> {
        final List<Object> values;
        int index;
        MIterator(List<Object> values){this.values=new ArrayList<>(values);this.index=0;}
        boolean hasNext(){return index<values.size();}
        Object next(){if(!hasNext())throw new NoSuchElementException();return values.get(index++);}
        List<Object> remaining(){return new ArrayList<>(values.subList(Math.min(index,values.size()),values.size()));}
        public Iterator<Object> iterator(){return values.subList(Math.min(index,values.size()),values.size()).iterator();}
    }

    private static final class MRange {
        final int start,stop,step;
        MRange(int s,int e,int p){start=s;stop=e;step=p;}
    }

    private static final class TupleValue {
        final List<Object> values;
        TupleValue(List<Object> v){values=List.copyOf(v);}
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value; ReturnSignal(Object v){value=v;}
    }
    private static final class BreakSignal extends RuntimeException {}
    private static final class ContinueSignal extends RuntimeException {}

    private static Object getAttribute(Object target,String name){
        if(target instanceof MInstance i){
            if(i.fields.containsKey(name))return i.fields.get(name);
            if(i.clazz.attrs.containsKey(name)){
                Object v=i.clazz.attrs.get(name);
                if(v instanceof MFunction f)return f.bind(i);
                if(v instanceof MAsyncFunction f)return f.bind(i);
                return v;
            }
            Object v=i.clazz.lookup(name);
            if(v instanceof MFunction f)return f.bind(i);
            if(v!=null)return v;
            throw runtimeError("AttributeError: '"+i.clazz.name+"' no tiene atributo '"+name+"'");
        }
        if(target instanceof MModule m){
            if(m.attrs.containsKey(name)) return m.attrs.get(name);
            throw runtimeError("AttributeError: el módulo '"+m.name+"' no tiene atributo '"+name+"'");
        }
        if(target instanceof MClass c){
            Object v=c.lookup(name);
            if(v!=null)return v;
            throw runtimeError("AttributeError: la clase '"+c.name+"' no tiene atributo '"+name+"'");
        }
        if(target instanceof MSuper sup){
            Object v=sup.lookup(name);
            if(v!=null)return v;
            throw runtimeError("AttributeError: super object has no attribute '"+name+"'");
        }

        Callable method=collectionMethod(target,name);
        if(method!=null)return method;

        if(target instanceof String s){
            if(name.equals("length"))return BigInteger.valueOf(s.length());
            if(name.equals("upper"))return (Callable)(a,k)->s.toUpperCase();
        }

        throw runtimeError("AttributeError: '"+typeName(target)+"' no tiene atributo '"+name+"'");
    }

    private static Callable collectionMethod(Object target,String name){
        if(target instanceof List<?> raw){
            @SuppressWarnings("unchecked") List<Object> list=(List<Object>)raw;
            return switch(name){
                case "append"->(a,k)->{requireStatic("append",a,1);list.add(a.get(0));return null;};
                case "extend"->(a,k)->{requireStatic("extend",a,1);list.addAll(iterable(a.get(0)));return null;};
                case "insert"->(a,k)->{requireStatic("insert",a,2);list.add(toInt(a.get(0)),a.get(1));return null;};
                case "pop"->(a,k)->{int i=a.isEmpty()?list.size()-1:toInt(a.get(0));if(i<0)i+=list.size();if(i<0||i>=list.size())throw runtimeError("IndexError: pop index out of range");return list.remove(i);};
                case "remove"->(a,k)->{requireStatic("remove",a,1);int i=list.indexOf(a.get(0));if(i<0)throw runtimeError("ValueError: list.remove(x): x not in list");list.remove(i);return null;};
                case "clear"->(a,k)->{list.clear();return null;};
                case "reverse"->(a,k)->{Collections.reverse(list);return null;};
                case "sort"->(a,k)->{list.sort(MCodeInterpreter::compare);if(truthy(k.getOrDefault("reverse",false)))Collections.reverse(list);return null;};
                case "index"->(a,k)->{requireStatic("index",a,1);int i=list.indexOf(a.get(0));if(i<0)throw runtimeError("ValueError: x is not in list");return BigInteger.valueOf(i);};
                case "count"->(a,k)->{requireStatic("count",a,1);return BigInteger.valueOf(list.stream().filter(x->Objects.equals(x,a.get(0))).count());};
                default->null;
            };
        }
        if(target instanceof Map<?,?> raw){
            @SuppressWarnings("unchecked") Map<Object,Object> map=(Map<Object,Object>)raw;
            return switch(name){
                case "get"->(a,k)->{requireStatic("get",a,1);Object v=map.get(a.get(0));return v==null&&a.size()>1?a.get(1):v;};
                case "keys"->(a,k)->new ArrayList<>(map.keySet());
                case "values"->(a,k)->new ArrayList<>(map.values());
                case "items"->(a,k)->{List<Object> out=new ArrayList<>();for(Map.Entry<Object,Object> e:map.entrySet())out.add(new TupleValue(List.of(e.getKey(),e.getValue())));return out;};
                case "pop"->(a,k)->{requireStatic("pop",a,1);if(!map.containsKey(a.get(0))){if(a.size()>1)return a.get(1);throw runtimeError("KeyError: "+stringify(a.get(0)));}return map.remove(a.get(0));};
                case "update"->(a,k)->{requireStatic("update",a,1);if(a.get(0) instanceof Map<?,?> m)m.forEach(map::put);else throw runtimeError("TypeError: update() argument must be a mapping");return null;};
                case "clear"->(a,k)->{map.clear();return null;};
                default->null;
            };
        }
        if(target instanceof Set<?> raw){
            @SuppressWarnings("unchecked") Set<Object> set=(Set<Object>)raw;
            return switch(name){
                case "add"->(a,k)->{requireStatic("add",a,1);set.add(a.get(0));return null;};
                case "remove"->(a,k)->{requireStatic("remove",a,1);if(!set.remove(a.get(0)))throw runtimeError("KeyError: "+stringify(a.get(0)));return null;};
                case "discard"->(a,k)->{requireStatic("discard",a,1);set.remove(a.get(0));return null;};
                case "clear"->(a,k)->{set.clear();return null;};
                case "union"->(a,k)->{requireStatic("union",a,1);Set<Object> out=new LinkedHashSet<>(set);out.addAll(iterable(a.get(0)));return out;};
                case "intersection"->(a,k)->{requireStatic("intersection",a,1);Set<Object> out=new LinkedHashSet<>(set);out.retainAll(iterable(a.get(0)));return out;};
                default->null;
            };
        }
        if(target instanceof String s){
            return switch(name){
                case "upper"->(a,k)->s.toUpperCase();
                case "lower"->(a,k)->s.toLowerCase();
                case "strip"->(a,k)->s.strip();
                case "replace"->(a,k)->{requireStatic("replace",a,2);return s.replace(stringify(a.get(0)),stringify(a.get(1)));};
                case "split"->(a,k)->{if(a.isEmpty())return List.of(s);String sep=stringify(a.get(0));return List.of(s.split(java.util.regex.Pattern.quote(sep)));};
                case "startswith"->(a,k)->{requireStatic("startswith",a,1);return s.startsWith(stringify(a.get(0)));};
                case "endswith"->(a,k)->{requireStatic("endswith",a,1);return s.endsWith(stringify(a.get(0)));};
                default->null;
            };
        }
        if(target instanceof TupleValue tuple){
            return switch(name){
                case "count"->(a,k)->{requireStatic("count",a,1);return BigInteger.valueOf(tuple.values.stream().filter(x->Objects.equals(x,a.get(0))).count());};
                case "index"->(a,k)->{requireStatic("index",a,1);int i=tuple.values.indexOf(a.get(0));if(i<0)throw runtimeError("ValueError: tuple.index(x): x not in tuple");return BigInteger.valueOf(i);};
                default->null;
            };
        }
        return null;
    }

    private static void setAttribute(Object target,String name,Object value){
        if(target instanceof MInstance i){i.fields.put(name,value);return;}
        if(target instanceof MClass c){c.attrs.put(name,value);return;}
        if(target instanceof MModule m){m.attrs.put(name,value);return;}
        throw runtimeError("AttributeError: '"+typeName(target)+"' no tiene atributos asignables");
    }

    private static void deleteAttribute(Object target,String name){
        if(target instanceof MInstance i){if(i.fields.remove(name)!=null)return;throw runtimeError("AttributeError: no existe '"+name+"'");}
        if(target instanceof MClass c){if(c.attrs.remove(name)!=null)return;throw runtimeError("AttributeError: no existe '"+name+"'");}
        if(target instanceof MModule m){if(m.attrs.remove(name)!=null)return;throw runtimeError("AttributeError: no existe '"+name+"'");}
        throw runtimeError("AttributeError: no se puede borrar el atributo '"+name+"'");
    }

    private static Object readSubscript(Object target,Object index){
        if(target instanceof Map<?,?> map){if(!map.containsKey(index))throw runtimeError("KeyError: "+stringify(index));return map.get(index);}
        int i=indexValue(index,length(target));
        if(target instanceof String s)return String.valueOf(s.charAt(i));
        if(target instanceof List<?> l)return l.get(i);
        if(target instanceof TupleValue t)return t.values.get(i);
        if(target instanceof MRange r){int v=r.start+i*r.step;if((r.step>0&&v>=r.stop)||(r.step<0&&v<=r.stop))throw runtimeError("IndexError: range index out of range");return BigInteger.valueOf(v);}
        throw runtimeError("TypeError: '"+typeName(target)+"' no admite índices");
    }

    private static Object readSlice(Object target,Slice s,Environment env){
        int size=length(target);
        int step=s.step()==null?1:toInt(ACTIVE.get().eval(s.step(),env));
        if(step==0)throw runtimeError("ValueError: slice step cannot be zero");
        int start=s.start()==null?(step>0?0:size-1):sliceBound(toInt(ACTIVE.get().eval(s.start(),env)),size,step>0);
        int stop=s.stop()==null?(step>0?size:-1):sliceBound(toInt(ACTIVE.get().eval(s.stop(),env)),size,step>0);
        if(target instanceof String str){StringBuilder b=new StringBuilder();if(step>0)for(int i=start;i<stop;i+=step)b.append(str.charAt(i));else for(int i=start;i>stop;i+=step)b.append(str.charAt(i));return b.toString();}
        List<Object> src=iterable(target),out=new ArrayList<>();
        if(step>0)for(int i=start;i<stop;i+=step)out.add(src.get(i));else for(int i=start;i>stop;i+=step)out.add(src.get(i));
        return out;
    }

    private static int sliceBound(int v,int size,boolean positive){
        if(v<0)v+=size;
        if(positive)return Math.max(0,Math.min(size,v));
        return Math.max(-1,Math.min(size-1,v));
    }

    private static void writeSubscript(Object target,Object index,Object value){
        if(target instanceof Map<?,?> raw){@SuppressWarnings("unchecked") Map<Object,Object> m=(Map<Object,Object>)raw;m.put(index,value);return;}
        int i=indexValue(index,length(target));
        if(target instanceof List<?> raw){@SuppressWarnings("unchecked") List<Object> l=(List<Object>)raw;l.set(i,value);return;}
        throw runtimeError("TypeError: objeto no admite asignaciones por índice");
    }

    private static Object unary(String op,Object v){
        return switch(op){
            case "not"->!truthy(v);
            case "+"->numberUnary(v,false);
            case "-"->numberUnary(v,true);
            case "~"->{if(v instanceof BigInteger i)yield i.not();throw runtimeError("TypeError: bad operand type for ~");}
            default->throw runtimeError("RuntimeError: operador unario desconocido "+op);
        };
    }

    private static Object numberUnary(Object v,boolean neg){
        if(v instanceof BigInteger i)return neg?i.negate():i;
        if(v instanceof Number n)return neg?-n.doubleValue():n.doubleValue();
        throw runtimeError("TypeError: se esperaba un número");
    }

    private static Object binary(Object a,String op,Object b){
        if(op.equals("+")){
            if(a instanceof String||b instanceof String)return stringify(a)+stringify(b);
            if(a instanceof List<?>&&b instanceof List<?>){
                List<Object> o=new ArrayList<>((List<?>)a);
                o.addAll((List<?>)b);
                return o;
            }
            if(a instanceof TupleValue&&b instanceof TupleValue){
                List<Object> o=new ArrayList<>(((TupleValue)a).values);
                o.addAll(((TupleValue)b).values);
                return new TupleValue(o);
            }
        }

        if(op.equals("*")){
            if(a instanceof String s&&b instanceof BigInteger)return repeatString(s,toInt(b));
            if(b instanceof String s&&a instanceof BigInteger)return repeatString(s,toInt(a));
            if(a instanceof List<?> l&&b instanceof BigInteger)return repeatList(l,toInt(b));
            if(b instanceof List<?> l&&a instanceof BigInteger)return repeatList(l,toInt(a));
        }

        if(a instanceof BigInteger x&&b instanceof BigInteger y){
            if(op.equals("/")){
                if(y.signum()==0)throw runtimeError("ZeroDivisionError: division by zero");
                return x.doubleValue()/y.doubleValue();
            }
            if(op.equals("//")){
                if(y.signum()==0)throw runtimeError("ZeroDivisionError: integer division or modulo by zero");
                return floorDiv(x,y);
            }
            if(op.equals("%")){
                if(y.signum()==0)throw runtimeError("ZeroDivisionError: integer division or modulo by zero");
                return x.remainder(y);
            }

            return switch(op){
                case "+"->x.add(y);
                case "-"->x.subtract(y);
                case "*"->x.multiply(y);
                case "**"->pow(x,y);
                case "&"->x.and(y);
                case "|"->x.or(y);
                case "^"->x.xor(y);
                case "<<"->x.shiftLeft(y.intValueExact());
                case ">>"->x.shiftRight(y.intValueExact());
                default->throw runtimeError("TypeError: operación entera no válida: "+op);
            };
        }

        if(a instanceof Number x&&b instanceof Number y){
            double p=x.doubleValue(),q=y.doubleValue();
            if(op.equals("/")){
                if(q==0)throw runtimeError("ZeroDivisionError: division by zero");
                return p/q;
            }
            if(op.equals("//")){
                if(q==0)throw runtimeError("ZeroDivisionError: division by zero");
                return Math.floor(p/q);
            }
            if(op.equals("%")){
                if(q==0)throw runtimeError("ZeroDivisionError: modulo by zero");
                return p%q;
            }

            return switch(op){
                case "+"->p+q;
                case "-"->p-q;
                case "*"->p*q;
                case "**"->Math.pow(p,q);
                default->throw runtimeError("TypeError: operación numérica no válida: "+op);
            };
        }

        if(op.equals("=="))return Objects.equals(a,b);
        if(op.equals("!="))return !Objects.equals(a,b);

        throw runtimeError("TypeError: operación no válida entre "+typeName(a)+" y "+typeName(b));
    }

    private static BigInteger floorDiv(BigInteger a,BigInteger b){BigInteger[]q=a.divideAndRemainder(b);if(a.signum()!=b.signum()&&q[1].signum()!=0)return q[0].subtract(BigInteger.ONE);return q[0];}
    private static BigInteger pow(BigInteger a,BigInteger b){if(b.signum()<0)return BigInteger.valueOf((long)Math.pow(a.doubleValue(),b.doubleValue()));return a.pow(b.intValueExact());}
    private static String repeatString(String s,int n){if(n<0)return "";StringBuilder b=new StringBuilder(s.length()*n);for(int i=0;i<n;i++)b.append(s);return b.toString();}
    private static List<Object> repeatList(List<?> l,int n){List<Object>o=new ArrayList<>();for(int i=0;i<n;i++)o.addAll(l);return o;}

    private static boolean comparison(Object a,String op,Object b){
        return switch(op){
            case "=="->Objects.equals(a,b);
            case "!="->!Objects.equals(a,b);
            case "is"->a==b;
            case "is not"->a!=b;
            case "in"->contains(b,a);
            case "not in"->!contains(b,a);
            case "<"->compare(a,b)<0;
            case "<="->compare(a,b)<=0;
            case ">"->compare(a,b)>0;
            case ">="->compare(a,b)>=0;
            default->throw runtimeError("RuntimeError: comparación desconocida "+op);
        };
    }

    private static boolean contains(Object container,Object value){
        if(container instanceof Map<?,?>m)return m.containsKey(value);
        return iterable(container).stream().anyMatch(x->Objects.equals(x,value));
    }

    private static int compare(Object a,Object b){
        if(a instanceof Number&&b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue());
        if(a instanceof String&&b instanceof String)return ((String)a).compareTo((String)b);
        if(a instanceof Comparable<?> c&&a.getClass().isInstance(b))return ((Comparable<Object>)c).compareTo(b);
        throw runtimeError("TypeError: valores no comparables");
    }

    private static boolean truthy(Object v){
        if(v==null)return false;
        if(v instanceof Boolean b)return b;
        if(v instanceof Number n)return n.doubleValue()!=0;
        if(v instanceof String s)return !s.isEmpty();
        if(v instanceof Collection<?> c)return !c.isEmpty();
        if(v instanceof Map<?,?>m)return !m.isEmpty();
        if(v instanceof TupleValue t)return !t.values.isEmpty();
        if(v instanceof MRange r)return rangeLength(r)>0;
        if(v instanceof MIterator it)return it.hasNext();
        return true;
    }

    private static int length(Object v){
        if(v instanceof String s)return s.length();
        if(v instanceof Collection<?> c)return c.size();
        if(v instanceof Map<?,?>m)return m.size();
        if(v instanceof TupleValue t)return t.values.size();
        if(v instanceof MRange r)return rangeLength(r);
        if(v instanceof MIterator it)return it.remaining().size();
        throw runtimeError("TypeError: object of type '"+typeName(v)+"' has no len()");
    }

    private static int rangeLength(MRange r){
        if(r.step>0)return r.start>=r.stop?0:(int)Math.ceil((r.stop-r.start)/(double)r.step);
        return r.start<=r.stop?0:(int)Math.ceil((r.start-r.stop)/(double)(-r.step));
    }

    private static int indexValue(Object idx,int size){
        if(!(idx instanceof BigInteger i))throw runtimeError("TypeError: índices deben ser enteros");
        int x=i.intValueExact(); if(x<0)x+=size; if(x<0||x>=size)throw runtimeError("IndexError: índice fuera de rango"); return x;
    }

    private static List<Object> iterable(Object v){
        if(v instanceof List<?>l)return new ArrayList<>(l);
        if(v instanceof TupleValue t)return new ArrayList<>(t.values);
        if(v instanceof Set<?>s)return new ArrayList<>(s);
        if(v instanceof Map<?,?>m)return new ArrayList<>(m.keySet());
        if(v instanceof String s){List<Object>o=new ArrayList<>();s.chars().forEach(c->o.add(String.valueOf((char)c)));return o;}
        if(v instanceof MRange r){List<Object>o=new ArrayList<>();for(int x=r.start;r.step>0?x<r.stop:x>r.stop;x+=r.step)o.add(BigInteger.valueOf(x));return o;}
        if(v instanceof MGenerator g)return new ArrayList<>(g.values);
        if(v instanceof MIterator it){List<Object> out=new ArrayList<>();while(it.hasNext())out.add(it.next());return out;}
        throw runtimeError("TypeError: '"+typeName(v)+"' no es iterable");
    }

    private static boolean isInstance(Object v,MClass c){
        if(v instanceof MInstance i)return i.clazz.isSubclassOf(c);
        return false;
    }

    private static String exceptionTypeOf(Object v){return v instanceof MCodeException e?e.typeName:typeName(v);}

    private static MCodeException exceptionFrom(Object v){
        if(v instanceof MCodeException e)return e;
        if(v instanceof MExceptionType t)return new MCodeException(t.name,null);
        throw runtimeError("TypeError: exceptions must derive from BaseException");
    }

    private static String typeName(Object v){
        if(v==null)return "NoneType";
        if(v instanceof BigInteger)return "int";
        if(v instanceof Double||v instanceof Float)return "float";
        if(v instanceof Boolean)return "bool";
        if(v instanceof String)return "str";
        if(v instanceof List<?>)return "list";
        if(v instanceof TupleValue)return "tuple";
        if(v instanceof Set<?>)return "set";
        if(v instanceof Map<?,?>)return "dict";
        if(v instanceof MRange)return "range";
        if(v instanceof MFunction)return "function";
        if(v instanceof MAsyncFunction)return "function";
        if(v instanceof MCoroutine)return "coroutine";
        if(v instanceof MGenerator)return "generator";
        if(v instanceof MIterator)return "iterator";
        if(v instanceof MModule)return "module";
        if(v instanceof MClass c)return "type";
        if(v instanceof MInstance i)return i.clazz.name;
        if(v instanceof MExceptionType)return "type";
        if(v instanceof Callable)return "function";
        return v.getClass().getSimpleName();
    }

    private static String stringify(Object v){
        if(v==null)return "None";
        if(v instanceof Boolean b)return b?"True":"False";
        if(v instanceof String)return (String)v;
        if(v instanceof BigInteger||v instanceof Double||v instanceof Float)return String.valueOf(v);
        if(v instanceof MRange r)return "range("+r.start+", "+r.stop+", "+r.step+")";
        if(v instanceof TupleValue t)return "("+join(t.values,", ",true)+")";
        if(v instanceof List<?>l)return "["+join(l,", ",false)+"]";
        if(v instanceof Set<?>s)return "{"+join(s,", ",false)+"}";
        if(v instanceof Map<?,?>m){StringBuilder b=new StringBuilder("{");boolean first=true;for(Map.Entry<?,?>e:m.entrySet()){if(!first)b.append(", ");first=false;b.append(stringify(e.getKey())).append(": ").append(stringify(e.getValue()));}return b.append("}").toString();}
        if(v instanceof MClass c)return c.name;
        if(v instanceof MInstance i)return "<"+i.clazz.name+" object>";
        if(v instanceof MFunction f)return "<function "+f.name+">";
        if(v instanceof MAsyncFunction)return "<function>";
        if(v instanceof MCoroutine)return "<coroutine>";
        if(v instanceof MGenerator g)return "<generator object ("+g.values.size()+" values)>";
        if(v instanceof MIterator)return "<iterator>";
        if(v instanceof MModule m)return "<module '"+m.name+"'>";
        if(v instanceof MExceptionType e)return e.name;
        if(v instanceof MCodeException e)return e.typeName+(e.detail.isEmpty()?"":": "+e.detail);
        if(v instanceof MSuper)return "<super object>";
        return String.valueOf(v);
    }

    private static String join(Collection<?> c,String sep,boolean tupleOne){
        StringBuilder b=new StringBuilder();boolean first=true;for(Object x:c){if(!first)b.append(sep);first=false;b.append(stringify(x));}if(tupleOne&&c.size()==1)b.append(",");return b.toString();
    }

    private static BigInteger toBigInteger(Object v){
        if(v instanceof BigInteger i)return i;
        if(v instanceof Number n)return BigInteger.valueOf(n.longValue());
        throw runtimeError("TypeError: se esperaba un entero");
    }

    private static double toDouble(Object v){
        if(v instanceof Number n)return n.doubleValue();
        throw runtimeError("TypeError: se esperaba un número");
    }

    private static String asciiString(String s){
        StringBuilder out=new StringBuilder("'");
        for(int i=0;i<s.length();){
            int cp=s.codePointAt(i); i+=Character.charCount(cp);
            if(cp>=32&&cp<127&&cp!='\\'&&cp!='\'')out.appendCodePoint(cp);
            else if(cp=='\\')out.append("\\\\");
            else if(cp=='\'')out.append("\\'");
            else if(cp=='\n')out.append("\\n");
            else if(cp=='\r')out.append("\\r");
            else if(cp=='\t')out.append("\\t");
            else if(cp<=0xFF)out.append(String.format(Locale.ROOT,"\\x%02x",cp));
            else if(cp<=0xFFFF)out.append(String.format(Locale.ROOT,"\\u%04x",cp));
            else out.append(String.format(Locale.ROOT,"\\U%08x",cp));
        }
        return out.append("'").toString();
    }

    private static Map<String,Object> globalNames(){return lastVariables;}

    private static int toInt(Object v){if(v instanceof BigInteger i)return i.intValueExact();throw runtimeError("TypeError: se esperaba un entero");}
    private static void requireCount(String name,List<Object>a,int n){if(a.size()!=n)throw runtimeError("TypeError: "+name+"() esperaba "+n+" argumento(s)");}
    private static void requireStatic(String name,List<Object>a,int n){if(a.size()<n)throw runtimeError("TypeError: "+name+"() esperaba al menos "+n+" argumento(s)");}
    private static boolean containsYield(Node node){
        if(node==null) return false;
        if(node instanceof Yield) return true;
        if(node instanceof ExpressionStatement x) return containsYield(x.expression());
        if(node instanceof Assignment x) return containsYield(x.value())||x.targets().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof AugmentedAssignment x) return containsYield(x.target())||containsYield(x.value());
        if(node instanceof If x) return x.branches().stream().anyMatch(b->containsYield(b.body())||containsYield(b.condition())) || containsYield(x.elseBlock());
        if(node instanceof While x) return containsYield(x.condition())||containsYield(x.body())||containsYield(x.elseBlock());
        if(node instanceof For x) return containsYield(x.target())||containsYield(x.iterable())||containsYield(x.body())||containsYield(x.elseBlock());
        if(node instanceof Try x) return containsYield(x.body())||x.handlers().stream().anyMatch(h->containsYield(h.body())||containsYield(h.type()))||containsYield(x.elseBlock())||containsYield(x.finallyBlock());
        if(node instanceof With x) return x.items().stream().anyMatch(i->containsYield(i.context()))||containsYield(x.body());
        if(node instanceof Match x) return containsYield(x.subject())||x.cases().stream().anyMatch(c->containsYield(c.pattern())||containsYield(c.guard())||containsYield(c.body()));
        if(node instanceof Return x) return containsYield(x.value());
        if(node instanceof Raise x) return containsYield(x.value());
        if(node instanceof FunctionDef || node instanceof AsyncFunctionDef || node instanceof ClassDef || node instanceof Lambda) return false;
        if(node instanceof Block b) return b.statements().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof Unary x) return containsYield(x.expression());
        if(node instanceof Binary x) return containsYield(x.left())||containsYield(x.right());
        if(node instanceof Comparison x) return containsYield(x.left())||x.rights().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof Call x) return containsYield(x.callee())||x.arguments().stream().anyMatch(a->containsYield(a.value()));
        if(node instanceof Attribute x) return containsYield(x.target());
        if(node instanceof Subscript x) return containsYield(x.target())||containsYield(x.subscript());
        if(node instanceof Await x) return containsYield(x.expression());
        if(node instanceof ListExpr x) return x.elements().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof TupleExpr x) return x.elements().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof SetExpr x) return x.elements().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof DictExpr x) return x.entries().stream().anyMatch(e->containsYield(e.key())||containsYield(e.value()));
        if(node instanceof Slice x) return containsYield(x.start())||containsYield(x.stop())||containsYield(x.step());
        if(node instanceof ListComp x) return containsYield(x.element())||x.generators().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof SetComp x) return containsYield(x.element())||x.generators().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof DictComp x) return containsYield(x.key())||containsYield(x.value())||x.generators().stream().anyMatch(MCodeInterpreter::containsYield);
        if(node instanceof Comprehension x) return containsYield(x.target())||containsYield(x.iterable())||x.conditions().stream().anyMatch(MCodeInterpreter::containsYield);
        return false;
    }

    private static final Set<String> EXCEPTION_NAMES=Set.of(
            "BaseException","Exception","RuntimeError","ValueError","TypeError",
            "NameError","IndexError","KeyError","ZeroDivisionError","ArithmeticError",
            "AttributeError","SyntaxError","ImportError","ModuleNotFoundError"
    );

    private static MCodeException runtimeError(String message){
        if(message!=null){
            int colon=message.indexOf(": ");
            if(colon>0){
                String type=message.substring(0,colon);
                if(EXCEPTION_NAMES.contains(type)){
                    return new MCodeException(type,message.substring(colon+2));
                }
            }
        }
        return new MCodeException("RuntimeError",message);
    }
}
