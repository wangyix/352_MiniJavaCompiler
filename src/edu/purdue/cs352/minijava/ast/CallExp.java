package edu.purdue.cs352.minijava.ast;

import java.util.*;

import edu.purdue.cs352.minijava.parser.Token;

public class CallExp extends Exp {
    Exp target;
    String method;
    List<Exp> arguments;

    public CallExp(Token tok, Exp target, String method, List<Exp> arguments) {
        super(tok);
        this.target = target;
        this.method = method;
        this.arguments = arguments;
    }

    @Override public ASTNode[] children() {
        ASTNode[] ret = new ASTNode[arguments.size() + 1];
        ret[0] = target;

        int i = 1;
        for (Exp e : arguments) {
            ret[i++] = e;
        }

        return ret;
    }
    public Exp getTarget() { return target; }
    public String getMethod() { return method; }
    public List<Exp> getArguments() { return arguments; }
    
    public void setTarget(Exp target) { this.target = target; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
