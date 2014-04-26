package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class NotExp extends Exp {
    Exp sub;

    public NotExp(Token tok, Exp sub) {
        super(tok);
        this.sub = sub;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{sub}; }
    public Exp getSub() { return sub; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
