package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class BooleanLiteralExp extends Exp {
    boolean value;

    public BooleanLiteralExp(Token tok, boolean value) {
        super(tok);
        this.value = value;
    }

    public boolean getValue() { return this.value; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
