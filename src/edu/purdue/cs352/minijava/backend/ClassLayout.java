package edu.purdue.cs352.minijava.backend;

import java.util.*;

import edu.purdue.cs352.minijava.ssa.*;

public class ClassLayout {
	
	
	// get the number of fields in an instance of this object
	public static int objectFields(SSAProgram prog, SSAClass cl) {

		int numFields = 0;
		
		while (true) {
			numFields += cl.getFieldsOrdered().size();
			
			// find superclass
			String superclassName = cl.getASTNode().getExtends();
			if (superclassName==null || superclassName.equals("Object"))
				break;
			
			cl = prog.getClasses().get(superclassName);
		}
		return numFields;
	}

	
	// get the size of an object (its number of fields plus one for the vtable)
	public static int objectSize(SSAProgram prog, SSAClass cl) {
		return (objectFields(prog, cl) + 1);	// size in words
	}

	
	
	// get the offset of a field within an object
	public static int fieldOffset(SSAProgram prog, SSAClass cl, String field) {
		
		int offset = objectSize(prog, cl) - 1;
		
		// search fields of classes backwards in child-to-parent order
		classloop:
		while (true) {
			
			List<SSAField> fields = cl.getFieldsOrdered();
			for (int i=fields.size()-1; i>=0; i--) {
				if (fields.get(i).getName().equals(field)) {
					break classloop;
				}
				offset--;
			}
			
			// find superclass
			String superclassName = cl.getASTNode().getExtends();
			if (superclassName==null || superclassName.equals("Object"))
				break;
			cl = prog.getClasses().get(superclassName);
		}
		
		return offset;
	}

	
	
	// a vtable
	public static class Vtable {
		public final List<String> methods;
		public final Map<String, Integer> methodOffsets;

		public Vtable(List<String> methods) {
			this.methods = methods;

			methodOffsets = new HashMap<String, Integer>();
			int off = 0;
			for (String m : methods)
				methodOffsets.put(m, off++);
		}
	}

	// get the complete vtable layout for this class
	public static Vtable getVtable(SSAProgram prog, SSAClass cl) {
		
		// build stack of classes with base class at top
		Stack<SSAClass> parentsStack = new Stack<SSAClass>();
		while (true) {
			parentsStack.push(cl);
			// find superclass
			String superclassName = cl.getASTNode().getExtends();
			if (superclassName==null || superclassName.equals("Object"))
				break;
			cl = prog.getClasses().get(superclassName);
		}
		
		
		// linkedhashset preserves insertion order
		LinkedHashSet<String> vtableMethods = new LinkedHashSet<String>();
		
		// build methods list in parent-to-child order.
		while (!parentsStack.isEmpty()) {
			
			SSAClass currentClass = parentsStack.pop();
			for (SSAMethod ssaMethod : currentClass.getMethodsOrdered()) {
				String methodName = ssaMethod.getMethod().getName();
				vtableMethods.add(methodName);
			}
		}
		
		return new Vtable(new ArrayList<String>(vtableMethods));
	}

	
	// get the size of the vtable for a class
	public static int vtableSize(SSAProgram prog, SSAClass cl) {
		return getVtable(prog, cl).methods.size();
	}

	// for a given method, get the implementing class
	public static SSAClass getImplementor(SSAProgram prog, SSAClass cl, String method) {
		
		while (true) {
			
			for (SSAMethod ssaMethod : cl.getMethodsOrdered()) {
				if (ssaMethod.getMethod().getName().equals(method)) {
					return cl;
				}
			}
			
			// find superclass
			String superclassName = cl.getASTNode().getExtends();
			if (superclassName==null || superclassName.equals("Object"))
				break;
			cl = prog.getClasses().get(superclassName);
		}
		
		return null;
	}
	
	
}