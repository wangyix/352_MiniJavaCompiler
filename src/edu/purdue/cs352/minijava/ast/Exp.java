package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class Exp extends ASTNode {
    public Exp(Token tok) { super(tok); }
    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
