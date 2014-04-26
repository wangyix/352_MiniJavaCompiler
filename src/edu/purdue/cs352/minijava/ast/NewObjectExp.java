package edu.purdue.cs352.minijava.ast;

import edu.purdue.cs352.minijava.parser.Token;

public class NewObjectExp extends Exp {
    String name;

    public NewObjectExp(Token tok, String name) {
        super(tok);
        this.name = name;
    }

    public String getName() { return name; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
