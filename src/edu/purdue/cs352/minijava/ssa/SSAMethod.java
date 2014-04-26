package edu.purdue.cs352.minijava.ssa;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.types.StaticType;

public class SSAMethod {
    Main main;
    MethodDecl method;
    List<SSAStatement> body;

    StaticType retType;
    List<StaticType> paramTypes;

    public SSAMethod(Main main, MethodDecl method, List<SSAStatement> body) {
        this.main = main;
        this.method = method;
        this.body = body;
    }

    public SSAMethod(Main main, List<SSAStatement> body) {
        this(main, null, body);
    }

    public SSAMethod(MethodDecl method, List<SSAStatement> body) {
        this(null, method, body);
    }

    public Main getMain() { return main; }
    public MethodDecl getMethod() { return method; }
    public List<SSAStatement> getBody() { return body; }
    public void setBody(List<SSAStatement> to) { body = to; }
    public void setRetType(StaticType retType) { this.retType = retType; }
    public StaticType getRetType() { return retType; }
    public void setParamTypes(List<StaticType> paramTypes) { this.paramTypes = paramTypes; }
    public List<StaticType> getParamTypes() { return paramTypes; }
    public StaticType getParamType(int param) {
        return paramTypes.get(param);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("    method ");
        if (method != null) {
            sb.append(method.getName());
        } else {
            sb.append("main");
        }
        sb.append(":\n");
        for (SSAStatement s : body) {
            sb.append("      ");
            sb.append(s.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}
