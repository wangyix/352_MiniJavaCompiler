package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class TypeIntArray extends Type {
    public TypeIntArray(Token tok) {
        super(tok, "int[]");
    }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
