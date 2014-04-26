package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class VarDecl extends ASTNode {
    Type type;
    String name;

    public VarDecl(Token tok, Type type, String name) {
        super(tok);
        this.type = type;
        this.name = name;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{type}; }
    public Type getType() { return type; }
    public String getName() { return name; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
