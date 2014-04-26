package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class ExpStatement extends Statement {
    Exp exp;

    public ExpStatement(Token tok, Exp exp) {
        super(tok);
        this.exp = exp;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{exp}; }
    public Exp getExp() { return exp; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
