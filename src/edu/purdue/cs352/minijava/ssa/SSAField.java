package edu.purdue.cs352.minijava.ssa;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.types.StaticType;

public class SSAField {
    VarDecl ast;
    String name;
    int idx;
    StaticType type;

    public SSAField(VarDecl ast, String name, int idx) {
        this.ast = ast;
        this.name = name;
        this.idx = idx;
    }

    public VarDecl getField() { return ast; }
    public String getName() { return name; }
    public int getIndex() { return idx; }
    public void setType(StaticType type) { this.type = type; }
    public StaticType getType() { return type; }
}
