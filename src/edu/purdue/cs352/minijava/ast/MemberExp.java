package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class MemberExp extends Exp {
    Exp sub;
    String member;

    public MemberExp(Token tok, Exp sub, String member) {
        super(tok);
        this.sub = sub;
        this.member = member;
    }

    @Override public ASTNode[] children() { return new ASTNode[]{sub}; }
    public Exp getSub() { return sub; }
    public String getMember() { return member; }

	public void setSub(Exp sub) { this.sub = sub; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
