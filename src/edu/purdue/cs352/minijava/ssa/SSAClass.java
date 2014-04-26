package edu.purdue.cs352.minijava.ssa;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.types.StaticType;

public class SSAClass {
    ClassDecl ast;

    List<SSAField> fieldsOrdered;
    Map<String, SSAField> fields;
    List<SSAMethod> methodsOrdered;
    Map<String, SSAMethod> methods;

    public SSAClass(ClassDecl ast, List<SSAMethod> methods) {
        this.ast = ast;

        // build the fields out of the class
        fields = new HashMap<String, SSAField>();
        fieldsOrdered = new ArrayList<SSAField>();
        int i = 0;
        for (VarDecl f : ast.getFields()) {
            String name = f.getName();
            SSAField field = new SSAField(f, name, i++);
            if (fields.containsKey(name)) {
            	throw new Error("Field '"+name+"' redeclared.");
            }
            fields.put(name, field);
            fieldsOrdered.add(field);
        }

        // and the methods from the (compiled) list
        this.methods = new HashMap<String, SSAMethod>();
        methodsOrdered = methods;
        i = 0;
        for (SSAMethod m : methods) {
        	String name = m.getMethod().getName();
            if (this.methods.containsKey(name)) {
        		throw new Error("Method "+name+" redeclared.");
            }
        	this.methods.put(name, m);
        }
    }

    // get the superclass of this class
    public SSAClass superclass(SSAProgram prog) {
        String sup = ast.getExtends();
        if (sup != null) return prog.getClass(sup);
        return null;
    }

    // get a field out of this class
    public SSAField getField(String name) {
        return fields.get(name);
    }

    // get a field out of this or any superclass
    public SSAField getField(SSAProgram prog, String name) {
        if (fields.containsKey(name)) {
            return fields.get(name);

        } else {
            SSAClass supc = superclass(prog);
            if (supc != null) return supc.getField(prog, name);

        }

        return null;
    }

    // get a method out of this class
    public SSAMethod getMethod(String name) {
        return methods.get(name);
    }

    // get the class which provides this method
    public SSAClass getMethodProvider(SSAProgram prog, String name) {
        if (methods.containsKey(name)) {
            return this;

        } else {
            SSAClass supc = superclass(prog);
            if (supc != null) return supc.getMethodProvider(prog, name);

        }

        return null;
    }

    // get a method out of this or any superclass
    public SSAMethod getMethod(SSAProgram prog, String name) {
        SSAClass prov = getMethodProvider(prog, name);
        if (prov != null) return prov.getMethod(name);
        return null;
    }

    public ClassDecl getASTNode() { return ast; }
    public Map<String, SSAField> getFields() { return fields; }
    public List<SSAField> getFieldsOrdered() { return fieldsOrdered; }
    public Map<String, SSAMethod> getMethods() { return methods; }
    public List<SSAMethod> getMethodsOrdered() { return methodsOrdered; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("  class ");
        sb.append(ast.getName());
        sb.append(":\n");

        for (SSAMethod m : methodsOrdered) {
            sb.append(m.toString());
        }

        return sb.toString();
    }
}
