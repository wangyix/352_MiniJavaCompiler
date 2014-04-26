package edu.purdue.cs352.minijava.ast;

import java.util.*;

import edu.purdue.cs352.minijava.parser.Token;

public class ClassDecl extends ASTNode {
    String name, eggstends;
    List<VarDecl> fields;
    List<MethodDecl> methods;

    public ClassDecl(Token tok, String name, String eggstends, List<VarDecl> fields, List<MethodDecl> methods) {
        super(tok);
        this.name = name;
        this.eggstends = eggstends;
        this.fields = fields;
        this.methods = methods;
    }

    @Override public ASTNode[] children() {
        ASTNode[] ret = new ASTNode[fields.size() + methods.size()];

        int i = 0;
        for (VarDecl vd : fields) {
            ret[i++] = vd;
        }
        for (MethodDecl md : methods) {
            ret[i++] = md;
        }

        return ret;
    }
    public String getName() { return name; }
    public String getExtends() { return eggstends; }
    public List<VarDecl> getFields() { return fields; }
    public List<MethodDecl> getMethods() { return methods; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
