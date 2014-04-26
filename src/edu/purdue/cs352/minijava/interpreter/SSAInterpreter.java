package edu.purdue.cs352.minijava.interpreter;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.parser.*;
import edu.purdue.cs352.minijava.ssa.*;
import edu.purdue.cs352.minijava.SSACompiler;

public class SSAInterpreter {
    SSAProgram prog;

    private static class StackFrame {
        public final IntObject thiz;
        public final Object[] params;

        public StackFrame(IntObject thiz, int pcount) {
            this.thiz = thiz;
            this.params = new Object[pcount];
        }
    }

    private static class IntObject {
        public Map<String, Object> fields;
        final public Map<String, SSAMethod> methods;

        public IntObject(SSAProgram prog, SSAClass cl) {
            fields = new HashMap<String, Object>();
            methods = new HashMap<String, SSAMethod>();
            addMembers(prog, cl);
        }

        private void addMembers(SSAProgram prog, SSAClass cl) {
            String eggstends = cl.getASTNode().getExtends();
            if (eggstends != null) {
                // get the super-members first
                SSAClass scl = prog.getClass(eggstends);
                if (scl != null)
                    addMembers(prog, scl);
            }

            // now add the local members
            for (SSAField f : cl.getFieldsOrdered())
                fields.put(f.getName(), null);
            for (SSAMethod m : cl.getMethodsOrdered())
                methods.put(m.getMethod().getName(), m);
        }
    }

    public static void main(String[] args) {
        ParserAST parser;
        Program progAST;
        SSAProgram prog;
        SSAInterpreter interp;

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
            progAST = parser.Program();
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            return;
        }

        prog = SSACompiler.compile(progAST);
        interp = new SSAInterpreter(prog);
        interp.execute();
    }


    public SSAInterpreter(SSAProgram prog) {
        this.prog = prog;
    }


    // run the program
    public void execute() {
        execute(prog.getMain(), new StackFrame(null, 0));
    }

    private Object execute(SSAMethod m, StackFrame frame) {
        Map<SSAStatement, Object> results = new HashMap<SSAStatement, Object>();
        Map<SSAStatement, List<SSAStatement>> unifications = new HashMap<SSAStatement, List<SSAStatement>>();
        SSAStatement[] body = m.getBody().toArray(new SSAStatement[0]);
        Map<String, Integer> labels = new HashMap<String, Integer>();

        // map all the labels and unifications
        for (int bi = 0; bi < body.length; bi++) {
            SSAStatement s = body[bi];
            switch (s.getOp()) {
                case Label:
                    labels.put((String) s.getSpecial(), Integer.valueOf(bi));
                    break;

                case Unify:
                case Alias:
                {
                    // get the full list
                    SSAStatement left = s.getLeft();
                    SSAStatement right = s.getRight();
                    List<SSAStatement> ulist, rlist;

                    if (unifications.containsKey(left)) {
                        ulist = new ArrayList<SSAStatement>(unifications.get(left));
                    } else {
                        ulist = new ArrayList<SSAStatement>();
                        ulist.add(left);
                    }

                    if (right != null) {
                        if (unifications.containsKey(right)) {
                            rlist = unifications.get(right);
                        } else {
                            rlist = new ArrayList<SSAStatement>();
                            rlist.add(right);
                        }
                        ulist.addAll(rlist);
                    }

                    ulist.add(s);

                    for (SSAStatement s2 : ulist) {
                        unifications.put(s2, ulist);
                    }
                    break;
                }
            }
        }

        // and run it
        for (int bi = 0; bi < body.length; bi++) {
            SSAStatement s = body[bi];
            Object result = null;

            SSAStatement.Op op = s.getOp();
            SSAStatement left = s.getLeft();
            SSAStatement right = s.getRight();
            Object special = s.getSpecial();

            switch (op) {
                // Meta:
                case Unify:
                case Alias:
                    result = results.get(left);
                    break;

                // Data:
                case This:
                    result = frame.thiz;
                    break;

                case Parameter:
                    result = frame.params[(Integer) special];
                    break;

                case Arg:
                    result = results.get(left);
                    break;

                case Null:
                {
                    Type ntype = (Type) special;
                    if (ntype instanceof TypeInt) {
                        result = Integer.valueOf(0);
                    } else if (ntype instanceof TypeBoolean) {
                        result = Boolean.FALSE;
                    } else {
                        result = null;
                    }
                    break;
                }

                case Int:
                case Boolean:
                    result = special;
                    break;

                case NewObj:
                    result = new IntObject(prog, prog.getClass((String) special));
                    break;

                case NewIntArray:
                    result = new int[((Integer) results.get(left)).intValue()];
                    break;

                // Control flow:
                case Label:
                    // nothing
                    break;

                case Goto:
                    bi = labels.get((String) special).intValue();
                    break;

                case Branch:
                case NBranch:
                {
                    boolean condition = ((Boolean) results.get(left)).booleanValue();
                    if (op == SSAStatement.Op.NBranch) condition = !condition;
                    if (condition) {
                        // branch
                        bi = labels.get((String) special).intValue();
                    }
                    break;
                }

                // Calls:
                case Call:
                {
                    SSACall call = (SSACall) special;
                    List<SSAStatement> args = call.getArgs();
                    StackFrame cframe = new StackFrame((IntObject) results.get(left), args.size());
                    SSAMethod cm = cframe.thiz.methods.get(call.getMethod());
                    List<Parameter> params = cm.getMethod().getParameters();

                    // match up our arguments to their parameters
                    if (args.size() != params.size())
                        throw new Error("Call with wrong number of arguments");

                    Iterator<SSAStatement> argI = args.iterator();

                    int pi = 0;
                    while (argI.hasNext()) {
                        Object arg = results.get(argI.next());
                        cframe.params[pi++] = arg;
                    }

                    // and run it
                    result = execute(cm, cframe);
                    break;
                }

                case Print:
                    System.out.println(results.get(left));
                    break;

                case Return:
                    return results.get(left);


                // Member access:
                case Member:
                {
                    Object target = results.get(left);
                    String field = (String) special;
                    if (field.equals("length") && target instanceof int[]) {
                        result = ((int[]) target).length;
                    } else {
                        IntObject obj = (IntObject) target;
                        result = obj.fields.get(field);
                    }
                    break;
                }

                case Index:
                {
                    int[] arr = (int[]) results.get(left);
                    int idx = ((Integer) results.get(right));
                    result = arr[idx];
                    break;
                }

                // Assignment:
                case VarAssg:
                    result = results.get(left);
                    break;

                case MemberAssg:
                {
                    IntObject obj = (IntObject) results.get(left);
                    result = results.get(right);
                    obj.fields.put((String) special, result);
                    break;
                }

                case IndexAssg:
                {
                    int[] arr = (int[]) results.get(left);
                    int idx = ((Integer) results.get((SSAStatement) special)).intValue();
                    result = results.get(right);
                    arr[idx] = ((Integer) result).intValue();
                    break;
                }

                // Unary operator:
                case Not:
                {
                    Object l = results.get(left);
                    if (l == Boolean.FALSE)
                        result = Boolean.TRUE;
                    else
                        result = Boolean.FALSE;
                    break;
                }

                // int-valued operators
                case Lt:
                case Le:
                case Gt:
                case Ge:
                case Plus:
                case Minus:
                case Mul:
                case Div:
                case Mod:
                {
                    int l = ((Integer) results.get(left)).intValue();
                    int r = ((Integer) results.get(right)).intValue();
                    switch (op) {
                        case Lt: result = Boolean.valueOf(l < r); break;
                        case Le: result = Boolean.valueOf(l <= r); break;
                        case Gt: result = Boolean.valueOf(l > r); break;
                        case Ge: result = Boolean.valueOf(l >= r); break;
                        case Plus: result = Integer.valueOf(l + r); break;
                        case Minus: result = Integer.valueOf(l - r); break;
                        case Mul: result = Integer.valueOf(l * r); break;
                        case Div: result = Integer.valueOf(l / r); break;
                        case Mod: result = Integer.valueOf(l % r); break;
                    }
                    break;
                }

                // boolean-valued operators
                case And:
                case Or:
                {
                    boolean l = ((Boolean) results.get(left)).booleanValue();
                    boolean r = ((Boolean) results.get(right)).booleanValue();
                    switch (op) {
                        case And: result = Boolean.valueOf(l && r); break;
                        case Or: result = Boolean.valueOf(l || r); break;
                    }
                    break;
                }

                // equality operators
                case Eq:
                case Ne:
                {
                    Object l = results.get(left);
                    Object r = results.get(right);

                    // get the Eq value first
                    if (l instanceof Integer) {
                        if (r instanceof Integer) {
                            result = Boolean.valueOf(l.equals(r));
                        } else {
                            result = Boolean.FALSE;
                        }
                    } else if (r instanceof Integer) {
                        result = Boolean.FALSE;
                    } else {
                        result = Boolean.valueOf(l == r);
                    }

                    // then swap for Ne
                    if (op == SSAStatement.Op.Ne)
                        result = Boolean.valueOf(result == Boolean.FALSE);

                    break;
                }

                default:
                    throw new Error("Implement SSA interpreter for " + op);
            }

            if (unifications.containsKey(s)) {
                for (SSAStatement s2 : unifications.get(s))
                    results.put(s2, result);
            } else {
                results.put(s, result);
            }
        }

        return null;
    }
}
