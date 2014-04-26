package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class IfStatement extends Statement {
    Exp condition;
    Statement ifPart, elsePart;

    public IfStatement(Token tok, Exp condition, Statement ifPart, Statement elsePart) {
        super(tok);
        this.condition = condition;
        this.ifPart = ifPart;
        this.elsePart = elsePart;
    }

    @Override public ASTNode[] children() {
        if (elsePart != null) {
            return new ASTNode[]{ condition, ifPart, elsePart };
        } else {
            return new ASTNode[]{ condition, ifPart };
        }
    }
    public Exp getCondition() { return condition; }
    public Statement getIfPart() { return ifPart; }
    public Statement getElsePart() { return elsePart; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
