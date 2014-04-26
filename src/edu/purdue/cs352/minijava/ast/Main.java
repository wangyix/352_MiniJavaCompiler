package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class Main extends ASTNode {
    Statement body;

    public Main(Token tok, Statement body) {
        super(tok);
        this.body = body;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{body}; }
    public Statement getBody() { return body; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
