package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class IndexExp extends Exp {
    Exp target, index;

    public IndexExp(Token tok, Exp target, Exp index) {
        super(tok);
        this.target = target;
        this.index = index;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{ target, index }; }
    public Exp getTarget() { return target; }
    public Exp getIndex() { return index; }

	public void setTarget(Exp target) { this.target = target; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
