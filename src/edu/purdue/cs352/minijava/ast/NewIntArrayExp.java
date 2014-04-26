package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class NewIntArrayExp extends Exp {
    Exp size;

    public NewIntArrayExp(Token tok, Exp size) {
        super(tok);
        this.size = size;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{size}; }
    public Exp getSize() { return size; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
