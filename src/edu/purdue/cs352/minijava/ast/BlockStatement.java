package edu.purdue.cs352.minijava.ast;

import java.util.*;

import edu.purdue.cs352.minijava.parser.Token;

public class BlockStatement extends Statement {
    List<Statement> body;

    public BlockStatement(Token tok, List<Statement> body) {
        super(tok);
        this.body = body;
    }

    @Override public ASTNode[] children() {
        return body.toArray(new ASTNode[body.size()]);
    }
    public List<Statement> getBody() { return body; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
