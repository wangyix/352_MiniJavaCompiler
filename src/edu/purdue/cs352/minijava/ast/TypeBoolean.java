package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class TypeBoolean extends Type {
    public TypeBoolean(Token tok) {
        super(tok, "boolean");
    }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
