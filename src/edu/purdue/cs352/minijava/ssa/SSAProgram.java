package edu.purdue.cs352.minijava.ssa;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;

public class SSAProgram {
    SSAMethod main;
    Map<String, SSAClass> classes;
    List<SSAClass> classesOrdered;

    public SSAProgram(SSAMethod main, List<SSAClass> classesOrdered) {
        this.main = main;
        this.classesOrdered = classesOrdered;

        classes = new HashMap<String, SSAClass>();
        for (SSAClass cl : classesOrdered) {
        	String className = cl.getASTNode().getName();
        	if (classes.containsKey(className)) {
        		throw new Error("Class '"+className+"' redeclared.");
        	}
        	classes.put(className, cl);
        }
    }

    public SSAMethod getMain() { return main; }
    public Map<String, SSAClass> getClasses() { return classes; }
    public SSAClass getClass(String name) {
        if (classes.containsKey(name)) return classes.get(name);
        return null;
    }
    public List<SSAClass> getClassesOrdered() { return classesOrdered; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("program:\n  main:\n");

        sb.append(main.toString());

        for (SSAClass cl : classesOrdered)
            sb.append(cl.toString());

        return sb.toString();
    }
}
