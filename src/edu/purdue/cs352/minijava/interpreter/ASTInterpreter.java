package edu.purdue.cs352.minijava.interpreter;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.parser.*;

public class ASTInterpreter extends ASTVisitor.SimpleASTVisitor {
    Program prog;
    Stack<StackFrame> stack;

    private static class StackFrame {
        public IntObject thiz;
        public Map<String, Object> vars;

        public StackFrame(IntObject thiz) {
            this.thiz = thiz;
            this.vars = new HashMap<String, Object>();
        }
    }

    private static class IntObject {
        public Map<String, Object> fields;
        final public Map<String, MethodDecl> methods;

        public IntObject(Program prog, ClassDecl cl) {
            fields = new HashMap<String, Object>();
            methods = new HashMap<String, MethodDecl>();
            addMembers(prog, cl);
        }

        private void addMembers(Program prog, ClassDecl cl) {
            String eggstends = cl.getExtends();
            if (eggstends != null) {
                // get the super-members first
                for (ClassDecl scl : prog.getClasses()) {
                    if (scl.getName().equals(eggstends)) {
                        addMembers(prog, scl);
                        break;
                    }
                }
            }

            // now add the local members
            for (VarDecl f : cl.getFields())
                fields.put(f.getName(), null);
            for (MethodDecl m : cl.getMethods())
                methods.put(m.getName(), m);
        }
    }


    public static void main(String[] args) {
        ParserAST parser;
        Program prog;
        ASTInterpreter interp;

        if (args.length != 1) {
            System.out.println("Use: mjinterp-ast <input file>");
            return;
        }

        try {
            parser = new ParserAST(new java.io.FileInputStream(args[0]));
        } catch (java.io.FileNotFoundException ex) {
            System.out.println("File " + args[0] + " not found.");
            return;
        }

        try {
            prog = parser.Program();
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            return;
        }

        interp = new ASTInterpreter(prog);
        prog.accept(interp);
    }


    public ASTInterpreter(Program prog) {
        this.prog = prog;
        stack = new Stack<StackFrame>();
    }

    // helper function to get classes
    private ClassDecl classByName(String name) {
        for (ClassDecl cl : prog.getClasses())
            if (cl.getName().equals(name)) return cl;
        throw new Error("Unknown class " + name);
    }

    @Override public Object defaultVisit(ASTNode node) {
        throw new Error("Implement AST interpreter for " + node.getClass().getSimpleName());
    }

    // the program itself just runs main
    @Override public Object visit(Program prog) {
        stack.push(new StackFrame(null));
        Object ret = prog.getMain().accept(this);
        stack.pop();
        return ret;
    }

    @Override public Object visit(Main main) {
        return main.getBody().accept(this);
    }

    @Override public Object visit(MethodDecl method) {
        StackFrame frame = stack.peek();
        for (VarDecl v : method.getVarDecls()) {
            Type t = v.getType();
            Object val = null;
            if (t instanceof TypeInt) {
                val = Integer.valueOf(0);
            } else if (t instanceof TypeBoolean) {
                val = Boolean.FALSE;
            }
            frame.vars.put(v.getName(), val);
        }

        for (Statement s : method.getBody()) s.accept(this);

        return method.getRetExp().accept(this);
    }

    // Statements:
    @Override public Object visit(BlockStatement stmt) {
        Object ret = null;
        for (Statement ss : stmt.getBody()) ret = ss.accept(this);
        return ret;
    }

    @Override public Object visit(ExpStatement stmt) {
        return stmt.getExp().accept(this);
    }

    @Override public Object visit(IfStatement ifs) {
        Object cond = ifs.getCondition().accept(this);
        Object ret = null;
        if (cond == Boolean.TRUE) {
            ret = ifs.getIfPart().accept(this);
        } else {
            Statement elsePart = ifs.getElsePart();
            if (elsePart != null) ret = elsePart.accept(this);
        }
        return ret;
    }

    @Override public Object visit(PrintStatement stmt) {
        Object ret = stmt.getValue().accept(this);
        System.out.println(ret);
        return ret;
    }

    @Override public Object visit(WhileStatement ws) {
        Exp cond = ws.getCondition();
        Statement body = ws.getBody();
        Object ret = null;

        while (cond.accept(this) == Boolean.TRUE)
            ret = body.accept(this);

        return ret;
    }

    // Expressions:
    @Override public Object visit(AssignExp exp) {
        Exp lhs = exp.getTarget();
        Exp rhs = exp.getValue();
        Object ret = null;

        if (lhs instanceof VarExp) {
            // easiest case
            String name = ((VarExp) lhs).getName();
            StackFrame frame = stack.peek();
            ret = rhs.accept(this);
            if (frame.vars.containsKey(name)) {
                frame.vars.put(name, ret);
            } else {
                frame.thiz.fields.put(name, ret);
            }

        } else if (lhs instanceof IndexExp) {
            IndexExp il = (IndexExp) lhs;
            int[] target = (int[]) il.getTarget().accept(this);
            int idx = ((Integer) il.getIndex().accept(this)).intValue();
            ret = rhs.accept(this);
            target[idx] = ((Integer) ret).intValue();

        } else {
            throw new Error("Implement = for " + lhs.getClass().getSimpleName());
        }

        return ret;
    }

    @Override public Object visit(BinaryExp exp) {
        String op = exp.getOp().toString();

        Object ret = null;
        Object lo, ro;

        lo = exp.getLeft().accept(this);
        ro = exp.getRight().accept(this);
        if (lo instanceof Integer && ro instanceof Integer) {
            int l, r;
            l = ((Integer) lo).intValue();
            r = ((Integer) ro).intValue();

            if (op.equals("<")) {
                ret = (l < r);
            } else if (op.equals("<=")) {
                ret = (l <= r);
            } else if (op.equals("==")) {
                ret = (l == r);
            } else if (op.equals("!=")) {
                ret = (l != r);
            } else if (op.equals(">")) {
                ret = (l > r);
            } else if (op.equals(">=")) {
                ret = (l >= r);
            } else if (op.equals("+")) {
                ret = (l + r);
            } else if (op.equals("-")) {
                ret = (l - r);
            } else if (op.equals("*")) {
                ret = (l * r);
            } else if (op.equals("/")) {
                ret = (l / r);
            } else if (op.equals("%")) {
                ret = (l % r);
            } else
                throw new Error("Implement BinaryExp for " + op);
        }

        return ret;
    }

    @Override public Object visit(BooleanLiteralExp exp) {
        return exp.getValue();
    }

    @Override public Object visit(CallExp exp) {
        Object target = exp.getTarget().accept(this);
        IntObject obj = (IntObject) target;
        String mname = exp.getMethod();

        List<Object> argvs = new ArrayList<Object>();
        for (Exp arg : exp.getArguments()) {
            argvs.add(arg.accept(this));
        }

        MethodDecl method = obj.methods.get(mname);

        // map all the arguments
        List<Parameter> parms = method.getParameters();
        if (argvs.size() != parms.size())
            throw new Error("Number of arguments does not match number of parameters!");
        StackFrame frame = new StackFrame(obj);
        Iterator<Object> argI = argvs.iterator();
        Iterator<Parameter> parmI = parms.iterator();
        while (argI.hasNext()) {
            Object arg = argI.next();
            Parameter parm = parmI.next();
            frame.vars.put(parm.getName(), arg);
        }

        // run the function
        stack.push(frame);
        Object ret = method.accept(this);
        stack.pop();

        return ret;
    }

    @Override public Object visit(IndexExp exp) {
        int[] arr = (int[]) exp.getTarget().accept(this);
        int idx = ((Integer) exp.getIndex().accept(this)).intValue();
        return Integer.valueOf(arr[idx]);
    }

    @Override public Object visit(IntLiteralExp exp) {
        return Integer.valueOf(exp.getValue());
    }

    @Override public Object visit(MemberExp exp) {
        Object obj = exp.getSub().accept(this);
        String mem = exp.getMember();
        if (obj instanceof IntObject) {
            return ((IntObject) obj).fields.get(mem);
        } else if (mem.equals("length")) {
            return ((int[]) obj).length;
        } else {
            throw new Error("Invalid member access");
        }
    }

    @Override public Object visit(NewObjectExp exp) {
        ClassDecl cl = classByName(exp.getName());
        return new IntObject(prog, cl);
    }

    @Override public Object visit(NewIntArrayExp exp) {
        int sz = ((Integer) exp.getSize().accept(this)).intValue();
        return new int[sz];
    }

    @Override public Object visit(NotExp exp) {
        Object sub = exp.getSub().accept(this);
        // FIXME: coercion
        if (sub == Boolean.FALSE) {
            return Boolean.TRUE;
        } else return Boolean.FALSE;
    }

    @Override public Object visit(ThisExp exp) {
        return stack.peek().thiz;
    }

    @Override public Object visit(VarExp exp) {
        StackFrame frame = stack.peek();
        String name = exp.getName();
        if (frame.vars.containsKey(name)) {
            return frame.vars.get(name);
        } else {
            return frame.thiz.fields.get(name);
        }
    }
}
