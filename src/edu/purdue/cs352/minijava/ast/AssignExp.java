package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class AssignExp extends Exp {
    Exp target, value;

    public AssignExp(Token tok, Exp target, Exp value) {
        super(tok);
        this.target = target;
        this.value = value;
    }

    @Override public ASTNode[] children() {
    	if (value==null)
 			return new ASTNode[]{target};
 		else
    		return new ASTNode[]{target, value};
    }
    
    public Exp getTarget() { return target; }
    public Exp getValue() { return value; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
