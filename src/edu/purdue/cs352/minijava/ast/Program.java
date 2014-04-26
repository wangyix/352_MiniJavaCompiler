package edu.purdue.cs352.minijava.ast;

import java.util.*;

import edu.purdue.cs352.minijava.parser.Token;

public class Program extends ASTNode {
    Main main;
    List<ClassDecl> cds;

    public Program(Token tok, Main main, List<ClassDecl> cds) {
        super(tok);
        this.main = main;
        this.cds = cds;
    }

    @Override public ASTNode[] children() {
        ASTNode[] ret = new ASTNode[cds.size() + 1];
        ret[0] = main;

        int i = 1;
        for (ClassDecl cd : cds) {
            ret[i++] = cd;
        }

        return ret;
    }
    public Main getMain() { return main; }
    public List<ClassDecl> getClasses() { return cds; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
