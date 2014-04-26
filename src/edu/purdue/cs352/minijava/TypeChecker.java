package edu.purdue.cs352.minijava;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.ssa.*;
import edu.purdue.cs352.minijava.types.*;

public class TypeChecker {
	
	final static ObjectType objectType = new ObjectType("Object", null);
	final static PrimitiveType.IntType intType = new PrimitiveType.IntType();
	final static PrimitiveType.BooleanType booleanType = new PrimitiveType.BooleanType();
	final static ObjectType intArrayType = new ObjectType("int[]", objectType);
	final static VoidType voidType = new VoidType();
	
	final static Map<String, StaticType> minijavaTypes;
	static {
		minijavaTypes = new HashMap<String, StaticType>();
		minijavaTypes.put("Object", objectType);
		minijavaTypes.put("int", intType);
		minijavaTypes.put("boolean", booleanType);
		minijavaTypes.put("int[]", intArrayType);
	}
	
	
	SSAProgram prog;
	Map<String, ObjectType> definedTypes;

	
	
	public TypeChecker(SSAProgram prog) {
		this.prog = prog;
		definedTypes = new HashMap<String, ObjectType>();
	}

	public void typeCheck() {
		
		List<SSAClass> ssaClasses= prog.getClassesOrdered();
		
		
		// add object types in an order such that a parent type is always added before
		// its child types.  this will detect circular dependencies.
		
		int numTypesToAdd = ssaClasses.size();
		boolean addedAType = true;;
		while (numTypesToAdd>0 && addedAType) {
			
			addedAType = false;
			
			// add the first type whose supertype has been added already
			for (SSAClass ssaClass : ssaClasses) {	
				ClassDecl classDecl = ssaClass.getASTNode();
				String className = classDecl.getName();
				if (definedTypes.containsKey(className)) {
					continue;	// skip types that have already been added
				}
				String superClassName = classDecl.getExtends();
				if (superClassName==null || superClassName.equals("Object")) {
					definedTypes.put(className, new ObjectType(className, objectType));
					addedAType = true;
					numTypesToAdd--;
					break;
				}
				else if (definedTypes.containsKey(superClassName)) {
					definedTypes.put(className, new ObjectType(className,
							(ObjectType)definedTypes.get(superClassName)));
					addedAType = true;
					numTypesToAdd--;
					break;
				}
			}
		}
		if (numTypesToAdd > 0) {
			throw new Error("Parent type cannot be resolved or circular"
					+ " depencies exist.");
		}
		

		
		// assign types to fields and methods of each class
		
		for (SSAClass ssaClass : ssaClasses) {
			
			// assign types to SSAFields
			for (SSAField ssaField : ssaClass.getFieldsOrdered()) {
				String typeName = ssaField.getField().getType().getName();
				StaticType type = definedTypes.get(typeName);
				if (type == null) {
					type = minijavaTypes.get(typeName);
					if (type == null)
						throw new Error("'"+typeName+"' cannot be resolved to a type.");
				}
				ssaField.setType(type);
			}
			
			// assign return type and param types to SSAMethods
			for (SSAMethod ssaMethod : ssaClass.getMethodsOrdered()) {
				String retTypeName = ssaMethod.getMethod().getType().getName();
				StaticType retType = definedTypes.get(retTypeName);
				if (retType == null) {
					retType = minijavaTypes.get(retTypeName);
					if (retType == null)
						throw new Error("'"+retTypeName+"' cannot be resolved to a type.");
				}
				ssaMethod.setRetType(retType);
				
				List<Parameter> params = ssaMethod.getMethod().getParameters();
				List<StaticType> paramTypes = new ArrayList<StaticType>(params.size());
				for (Parameter param : params) {
					String paramTypeName = param.getType().getName();
					StaticType paramType = definedTypes.get(paramTypeName);
					if (paramType == null) {
						paramType = minijavaTypes.get(paramTypeName);
						if (paramType == null)
							throw new Error("'"+paramTypeName+"' cannot be resolved to a type.");
					}
					paramTypes.add(paramType);
				}
				ssaMethod.setParamTypes(paramTypes);
			}
		}
		
		
		// type check main
		for (SSAStatement ssaStatement : prog.getMain().getBody()) {
			typeCheckStatement(ssaStatement, null, null);
		}
		
		// type check body of each method of each class
		for (SSAClass ssaClass : ssaClasses) {
			for (SSAMethod ssaMethod : ssaClass.getMethodsOrdered()) {
				for (SSAStatement ssaStatement : ssaMethod.getBody()) {
					typeCheckStatement(ssaStatement, ssaMethod, ssaClass);
				}
			}
		}
	}
	
	
	
	
	// set ssaParentClass null for main method
	private void typeCheckStatement(SSAStatement st,
			SSAMethod currentMethod,
			SSAClass currentClass) {
		
		SSAStatement left = st.getLeft();
		SSAStatement right = st.getRight();
		Object special = st.getSpecial();
		
		boolean typesOk = true;
		String errorMsg = null;
		switch (st.getOp()) {
		case Unify: {
			if (!left.getType().equals(right.getType())) {
				errorMsg = "cannot unify statements of different types.";
				typesOk = false;
				break;
			}
			st.setType(left.getType());
			break;
		}
		case Alias: {
			st.setType(left.getType());
			break;
		}
		case This: {
			if (currentClass==null) {
				errorMsg = "cannot reference 'this' within main().";
				typesOk = false;
				break;
			}
			String typeName = currentClass.getASTNode().getName();
			st.setType(definedTypes.get(typeName));
			break;
		}
		case Parameter: {
			int position = (int)special;
			st.setType(currentMethod.getParamType(position));
			break;
		}
		case Arg: {
			st.setType(left.getType());
			break;
		}
		case Null: {
			String typeName = ((Type)special).getName();
			StaticType type = definedTypes.get(typeName);
			if (type==null) {
				type = minijavaTypes.get(typeName);
				if (type==null) {
					errorMsg = "type "+typeName+" could not be resolved.";
					typesOk = false;
					break;
				}
			}
			st.setType(type);
			break;
		}
		case Int: {
			st.setType(intType);
			break;
		}
		case Boolean: {
			st.setType(booleanType);
			break;
		}
		case NewObj: {
			String typeName = (String)special;
			if (!definedTypes.containsKey(typeName)) {
				errorMsg = "type "+typeName+" could not be resolved to a defined type.";
				typesOk = false;
				break;
			}
			st.setType(definedTypes.get(typeName));
			break;
		}
		case NewIntArray: {
			st.setType(intArrayType);
			break;
		}
		case Label:
		case Goto: {
			st.setType(voidType);
			break;
		}
		case Branch:
		case NBranch: {
			if (!left.getType().equals(booleanType)) {
				errorMsg = "branch condition does not evaluate to a boolean.";
				typesOk = false;
			}
			st.setType(voidType);
			break;
		}
		case Call: {
			if (!left.getType().isObject()) {
				errorMsg = "target is not an object type.";
				typesOk = false;
				break;
			}
			String classNameToCheck = ((ObjectType)left.getType()).getName();
			if (!definedTypes.containsKey(classNameToCheck)) {
				errorMsg = "type "+classNameToCheck+" could not be resolved to a defined type.";
				typesOk = false;
				break;
			} 
			SSACall call = (SSACall)special;
			
			// find method of the target's class
			String methodName = call.getMethod();
			SSAMethod method;
			do {
				SSAClass classToCheck = prog.getClasses().get(classNameToCheck);
				method = classToCheck.getMethods().get(methodName);
				if (method!=null) {
					break;
				}
				classNameToCheck = classToCheck.getASTNode().getExtends();
			} while (classNameToCheck!=null && !classNameToCheck.equals("Object"));
			if (method==null) {
				errorMsg = "method "+methodName+" could not be resolved.";
				typesOk = false;
				break;
			}
			
			// check number of args passed against number of params
			if (call.getArgs().size() != method.getParamTypes().size()) {
				errorMsg = "incorrect number of method arguments.";
				typesOk = false;
				break;
			}
			// type-check each arg passed against the params of the method
			for (SSAStatement arg : call.getArgs()) {
				StaticType argType = arg.getType();
				int position = (int)arg.getSpecial();
				StaticType paramType = method.getParamType(position);
				if (!argType.subtypeOf(paramType)) {
					errorMsg = "arg "+position+" of method "+methodName+" has incorrecte type.";
					typesOk = false;
					break;
				}
			}
			st.setType(method.getRetType());
			break;
		}
		case Print: {
			if (!left.getType().equals(intType)) {
				errorMsg = "printed type must be an int.";
				typesOk = false;
				break;
			}
			st.setType(voidType);
			break;
		}
		case Return: {
			if (!left.getType().subtypeOf(currentMethod.getRetType())) {
				errorMsg = "return type is not compatible with declared return type of method.";
				typesOk = false;
				break;
			}
			st.setType(voidType);
			break;
		}
		case Member: {
			if (!left.getType().isObject()) {
				errorMsg = "target is not an object type.";
				typesOk = false;
				break;
			}
			String memberName = (String)special;
			if (left.getType().equals(intArrayType)
					&& memberName.equals("length")) {
				st.setType(intType);
				break;
			}
			String classNameToCheck = ((ObjectType)left.getType()).getName();			
			if (!definedTypes.containsKey(classNameToCheck)) {
				errorMsg = "type "+classNameToCheck+" could not be resolved to a defined type.";
				typesOk = false;
				break;
			} 
			// find member of the target's class
			
			SSAField field;
			do {
				SSAClass classToCheck = prog.getClasses().get(classNameToCheck);
				field = classToCheck.getFields().get(memberName);
				if (field!=null) {
					break;
				}
				classNameToCheck = classToCheck.getASTNode().getExtends();
			} while (classNameToCheck!=null && !classNameToCheck.equals("Object"));
			if (field==null) {
				errorMsg = "field "+memberName+" could not be resolved.";
				typesOk = false;
				break;
			}	
			st.setType(field.getType());
			break;
		}
		case Index: {
			if (!left.getType().equals(intArrayType)){
				errorMsg = "only type int[] can be subscripted.";  
				typesOk = false;
				break;
			}
			if (!right.getType().equals(intType)) {
				errorMsg = "index must be type int.";  
				typesOk = false;
				break;
			}
			st.setType(intType);
			break;
		}
		case Store:
		case Load: {
			break;
		}
		case VarAssg: {
			// the variable's declared type will always be the type of its last
			// value, which goes all the way back to a Parameter or Null
			StaticType varType = right.getType();
			st.setRight(null);		// revert right to what it should be
			if (!left.getType().subtypeOf(varType)) {
				errorMsg = "assigned type is not compatible with variable type.";
				typesOk = false;
				break;
			}
			st.setType(varType);
			break;
		}
		case MemberAssg: {
			if (!left.getType().isObject()){
				errorMsg = "target is not an object type.";
				typesOk = false;
				break;
			}
			String classNameToCheck = ((ObjectType)left.getType()).getName();
			if (!definedTypes.containsKey(classNameToCheck)) {
				errorMsg = "type "+classNameToCheck+" could not be resolved to a defined type.";
				typesOk = false;
				break;
			} 
			// find member of the target's class
			String memberName = (String)special;
			SSAField field;
			do {
				SSAClass classToCheck = prog.getClasses().get(classNameToCheck);
				field = classToCheck.getFields().get(memberName);
				if (field!=null) {
					break;
				}
				classNameToCheck = classToCheck.getASTNode().getExtends();
			} while (classNameToCheck!=null && !classNameToCheck.equals("Object"));
			if (field==null) {
				errorMsg = "field "+memberName+" could not be resolved.";
				typesOk = false;
				break;
			}
			if (!right.getType().subtypeOf(field.getType())) {
				errorMsg = "assigned type is not compatible with var type.";
				typesOk = false;
				break;
			}
			st.setType(field.getType());
			break;
		}
		case IndexAssg: {
			if (!left.getType().equals(intArrayType)) {
				errorMsg = "only type int[] can be subscripted.";  
				typesOk = false;
				break;
			}
			if (!right.getType().equals(intType)) {
				errorMsg = "assigned type must be int.";
				typesOk = false;
				break;
			}
			SSAStatement index = (SSAStatement)special;
			if (!index.getType().equals(intType)) {
				errorMsg =  "index must be type int.";  
				typesOk = false;
				break;
			}
			st.setType(intType);
			break;
		}
		case Not: {
			if (!left.getType().equals(booleanType)) {
				errorMsg = "only type boolean can have the not-operation applied to it.";
				typesOk = false;
				break;
			}
			st.setType(booleanType);
			break;
		}
		case Lt: 
		case Le:
		case Gt:
		case Ge: {
			if (!left.getType().equals(intType) 
					|| !right.getType().equals(intType)) {
				errorMsg = "only two ints can have <, >, <=, or >= applied to them.";
				typesOk = false;
				break;
			}
			st.setType(booleanType);
			break;
		}
		case Plus:
		case Minus:
		case Mul:
		case Div:
		case Mod: {
			if (!left.getType().equals(intType) 
					|| !right.getType().equals(intType)) {
				errorMsg = "only two ints can have +, -, /, *, or % applied to them.";
				typesOk = false;
				break;
			}
			st.setType(intType);
			break;
		}
		case And:
		case Or: {
			if (!left.getType().equals(booleanType) 
					|| !right.getType().equals(booleanType)) {
				errorMsg = "only two booleans can have && or || applied to them.";
				typesOk = false;
				break;
			}
			st.setType(booleanType);
			break;
		}
		case Eq:
		case Ne: {	// any two types are allowed
			st.setType(booleanType);
		}
		}
		if (!typesOk) {
			throw new Error("\nSSA statement "+st.getIndex()+": "+errorMsg);
		}
	}
}
