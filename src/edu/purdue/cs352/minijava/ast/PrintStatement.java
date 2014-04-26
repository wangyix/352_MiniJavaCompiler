package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class PrintStatement extends Statement {
    Exp value;

    public PrintStatement(Token tok, Exp value) {
        super(tok);
        this.value = value;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{value}; }
    public Exp getValue() { return value; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
