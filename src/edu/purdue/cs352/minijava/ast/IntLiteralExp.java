package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class IntLiteralExp extends Exp {
    int value;

    public IntLiteralExp(Token tok, int value) {
        super(tok);
        this.value = value;
    }

    public int getValue() { return value; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
