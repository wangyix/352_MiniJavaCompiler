package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class ThisExp extends Exp {
    public ThisExp(Token tok) { super(tok); }
    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
