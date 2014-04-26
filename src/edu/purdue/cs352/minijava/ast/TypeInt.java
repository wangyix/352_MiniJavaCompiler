package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class TypeInt extends Type {
    public TypeInt(Token tok) {
        super(tok, "int");
    }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
