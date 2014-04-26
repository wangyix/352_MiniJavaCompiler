package edu.purdue.cs352.minijava.ssa;

import java.util.*;

// used as the special of a SSA call to hold the method name and arguments
public class SSACall {
    String method;
    List<SSAStatement> args;

    public SSACall(String method, List<SSAStatement> args) {
        this.method = method;
        this.args = args;
    }

    public String getMethod() { return method; }
    public List<SSAStatement> getArgs() { return args; }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(method);
        sb.append("(");

        boolean first = true;
        for (SSAStatement arg : args) {
            if (first)
                first = false;
            else
                sb.append(", ");
            sb.append(arg.getIndex());
        }
        sb.append(")");

        return sb.toString();
    }
}
