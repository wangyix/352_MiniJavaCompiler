package edu.purdue.cs352.minijava.ast;

import java.util.*;

import edu.purdue.cs352.minijava.parser.Token;

public class MethodDecl extends ASTNode {
    Type retType;
    String name;
    List<Parameter> parameters;
    List<VarDecl> vds;
    List<Statement> body;
    Exp retExp;

    public MethodDecl(Token tok, Type retType, String name,
        List<Parameter> parameters, List<VarDecl> vds, List<Statement> body,
        Exp retExp) {

        super(tok);
        this.retType = retType;
        this.name = name;
        this.parameters = parameters;
        this.vds = vds;
        this.body = body;
        this.retExp = retExp;
    }

    @Override public ASTNode[] children() {
        ASTNode[] ret = new ASTNode[
            parameters.size() +
            vds.size() +
            body.size() +
            2];
        ret[0] = retType;

        int i = 1;
        for (Parameter p : parameters) {
            ret[i++] = p;
        }
        for (VarDecl vd : vds) {
            ret[i++] = vd;
        }
        for (Statement s : body) {
            ret[i++] = s;
        }
        ret[i++] = retExp;

        return ret;
    }
    public Type getType() { return retType; }
    public String getName() { return name; }
    public List<Parameter> getParameters() { return parameters; }
    public List<VarDecl> getVarDecls() { return vds; }
    public List<Statement> getBody() { return body; }
    public Exp getRetExp() { return retExp; }

    public Object accept(ASTVisitor visitor) { return visitor.visit(this); }
}
