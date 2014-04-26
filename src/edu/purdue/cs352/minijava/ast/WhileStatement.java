package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class WhileStatement extends Statement {
    Exp condition;
    Statement body;

    public WhileStatement(Token tok, Exp condition, Statement body) {
        super(tok);
        this.condition = condition;
        this.body = body;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{ condition, body }; }
    public Exp getCondition() { return condition; }
    public Statement getBody() { return body; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
