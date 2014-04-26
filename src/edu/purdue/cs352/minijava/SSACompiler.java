package edu.purdue.cs352.minijava;

import java.util.*;

import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.ssa.*;

public class SSACompiler extends ASTVisitor.SimpleASTVisitor {
	
	// The method body currently being compiled.
	static List<SSAStatement> body = null;

	// local vars in a method, which includes declard local vars and method params
	static Map<String, SSAStatement> localVars = new HashMap<String, SSAStatement>();
	
	// used to store the SSA-pointers of local vars before they are assigned a new
	// value inside an if/else/while body.  Used to generate unify-SSAStatements.
	static Map<String, SSAStatement> localVarsPreAssign = null;
	
	
	public static SSAProgram compile(Program prog) {
		SSAMethod main = compile(prog.getMain());
		List<SSAClass> classes = new ArrayList<SSAClass>();

		for (ClassDecl cl : prog.getClasses())
			classes.add(compile(cl));

		return new SSAProgram(main, classes);
	}

	public static SSAClass compile(ClassDecl cl) {
		List<SSAMethod> methods = new ArrayList<SSAMethod>();
		for (MethodDecl md : cl.getMethods())
			methods.add(compile(md));
		return new SSAClass(cl, methods);
	}

	public static SSAMethod compile(Main main) {
		SSACompiler compiler = new SSACompiler();
		
		// start a new body of SSAStatements
		body = new ArrayList<SSAStatement>();
		
		// there's only a body
		main.getBody().accept(compiler);
		
		return new SSAMethod(main, body);//compiler.getBody());
	}

	
	public static SSAMethod compile(MethodDecl method) {
		SSACompiler compiler = new SSACompiler();

		// reset the SSAStatements, params, localvars for this method body?
		body = new ArrayList<SSAStatement>();
		localVars.clear();
		//localVarsPreAssign = null;	// shouldn't be necessary

		
		// add parameters
		int position = 0;
		for (Parameter p : method.getParameters()) {
		
			String paramName = p.getName();
			
			if (localVars.containsKey(paramName)) {
				throw new Error("Duplicate local variables: "+paramName);
			} 
			
			// For each parameter, we first need a Parameter-SSAStatement, and then
			// a VarAssg-SSAStatement referring to the Parameter-SSAStatement.
			SSAStatement paramSSA = new SSAStatement(p, SSAStatement.Op.Parameter,
					position);
			body.add(paramSSA);
			
			localVars.put(paramName, paramSSA);
			position++;
		}
		for (Parameter p : method.getParameters()) {
			
			String paramName = p.getName();
			SSAStatement assgSSA = new SSAStatement(p, SSAStatement.Op.VarAssg,
					localVars.get(paramName), 
					localVars.get(paramName),	// points to paramSSA so var type can be known
					paramName);
			body.add(assgSSA);
			localVars.put(paramName, assgSSA);			
		}

		
		// add declared variables of the method
		for (VarDecl v : method.getVarDecls()) {
		
			String varName = v.getName();
			
			if (localVars.containsKey(varName)) {
				throw new Error("Duplicate local variables: "+varName);
			}
			
			// for each declared var, we need a Null-SSAStatement
			SSAStatement nullSSA = new SSAStatement(v, SSAStatement.Op.Null,
					v.getType());
			body.add(nullSSA);
			localVars.put(varName, nullSSA);
		}


		// then compile the body
		for (Statement s : method.getBody()) {
			s.accept(compiler);
		}

		// and the return
		compiler.compileReturn(method.getRetExp());
		
		
		// return a new SSAMethod with the method ASTNode
		// and the SSAStatements for this method
		SSAMethod ret = new SSAMethod(method, body);//compiler.getBody());
		return ret;
	}


	public void compileReturn(Exp retExp) {
		body.add(new SSAStatement(retExp, SSAStatement.Op.Return,
				(SSAStatement)retExp.accept(this), null));
	}
	
	public List<SSAStatement> getBody() { 
		return body; 
	}
		
	
	// assumes varName is a valid local var
	private static void updateLocalVarSSA(String varName, SSAStatement newSSA) {
		
		// see if localVarsPreAssign exists and does not contain this var.
		// if so, add it and its old SSA-pointer to it
		if (localVarsPreAssign!=null &&
				!localVarsPreAssign.containsKey(varName)) {
			localVarsPreAssign.put(varName, localVars.get(varName));
		}

		localVars.put(varName, newSSA);
	}
	
	
	
// overridden visit methods *********************************************************************************
	
	@Override
	public Object defaultVisit(ASTNode node) {
		throw new Error("Unsupported visitor in SSACompiler: " + node.getClass().getSimpleName());
	}
	
	@Override
	public Object visit(AssignExp exp) {
		// what sort of statement we make, if any, depends on the LHS
		Exp target = exp.getTarget();
		SSAStatement ret;	// return is SSAStatement of value

		if (target instanceof VarExp) {
			VarExp varTarget = (VarExp)target;
			String varTargetName = varTarget.getName();
			SSAStatement localVarTargetSSA;
						
			// look up this variable in the local vars table
			localVarTargetSSA = localVars.get(varTargetName);
			
			if (localVarTargetSSA!=null) {
							
				SSAStatement assgSSA = new SSAStatement(exp, SSAStatement.Op.VarAssg,
						(ret = (SSAStatement)exp.getValue().accept(this)),
						 localVars.get(varTargetName),	// points to old value so var type can be known
						 varTargetName);
				body.add(assgSSA);
		
				updateLocalVarSSA(varTargetName, assgSSA);
			}
			else {
				// assume it's a valid field of this class
				// add a This-SSAStatement and a MemberAssg-SSAStatement to the body
				SSAStatement thisSSA = new SSAStatement(varTarget, SSAStatement.Op.This);
				body.add(thisSSA);
				
				SSAStatement assgSSA = new SSAStatement(exp, SSAStatement.Op.MemberAssg,
						thisSSA,
						(ret = (SSAStatement)exp.getValue().accept(this)),
						varTargetName);
				
				body.add(assgSSA);
			}
			
		} else if (target instanceof MemberExp) {
			MemberExp memberTarget = (MemberExp)target;
			
			SSAStatement assgSSA = new SSAStatement(exp, SSAStatement.Op.MemberAssg,
					(SSAStatement)memberTarget.getSub().accept(this),
					(ret = (SSAStatement)exp.getValue().accept(this)),
					memberTarget.getMember());
					
			body.add(assgSSA);
			
		} else if (target instanceof IndexExp) {
			IndexExp indexTarget = (IndexExp)target;
			SSAStatement targetSSA = (SSAStatement)indexTarget.getTarget().accept(this);
			SSAStatement indexSSA = (SSAStatement)indexTarget.getIndex().accept(this);
			SSAStatement assgSSA = new SSAStatement(exp, SSAStatement.Op.IndexAssg,
					targetSSA,
					(ret = (SSAStatement)exp.getValue().accept(this)),
					indexSSA);
					
			body.add(assgSSA);
			
		} else {
			throw new Error("Invalid LHS: " + target.getClass().getSimpleName());
		}
		
		return ret;
	}
	
	
	
	
	@Override
	public Object visit(BinaryExp exp) {
		SSAStatement.Op SSAOp = null;
		String op = exp.getOp().toString();
		int opLength = op.length();
		char opFirstChar = op.charAt(0);
		switch (opFirstChar) {
		case '+':
			SSAOp = SSAStatement.Op.Plus;
			break;
		case '-':
			SSAOp = SSAStatement.Op.Minus;
			break;
		case '*':
			SSAOp = SSAStatement.Op.Mul;
			break;
		case '/':
			SSAOp = SSAStatement.Op.Div;
			break;
		case '%':
			SSAOp = SSAStatement.Op.Mod;
			break;
		case '&':
			SSAOp = SSAStatement.Op.And;
			break;
		case '|':
			SSAOp = SSAStatement.Op.Or;
			break;
		case '<':
			if (opLength==1)
				SSAOp = SSAStatement.Op.Lt;
			else
				SSAOp = SSAStatement.Op.Le;
			break;
		case '>':
			if (opLength==1)
				SSAOp = SSAStatement.Op.Gt;
			else
				SSAOp = SSAStatement.Op.Ge;
			break;
		case '=':
			SSAOp = SSAStatement.Op.Eq;
			break;
		case '!':
			SSAOp = SSAStatement.Op.Ne;
			break;
		}
		SSAStatement ret = new SSAStatement(exp, SSAOp,
				(SSAStatement)exp.getLeft().accept(this),
				(SSAStatement)exp.getRight().accept(this));
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(BlockStatement exp) {
		for (Statement s : exp.getBody()) {
			s.accept(this);
		}
		return null;
	}
	

	@Override
	public Object visit(BooleanLiteralExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Boolean, exp.getValue());
		body.add(ret);
		return ret;
	}
	
	
	// note: does not check if target is something you can call methods on.
	@Override
	public Object visit(CallExp exp) {
	
		// visit the target first
		SSAStatement SSATarget = (SSAStatement)exp.getTarget().accept(this);
		 
		// for each arg, we need to create an Arg-SSAStatement pointing to the
		// SSAStatement of the Exp being passed as the value for that arg.
		List<SSAStatement> SSAargs = new ArrayList<SSAStatement>();
		SSAStatement SSAArg;
		int position = 0;
		for (Exp e : exp.getArguments()) {
			SSAArg = new SSAStatement(e, SSAStatement.Op.Arg, 
					(SSAStatement)e.accept(this), null,
					position);
			body.add(SSAArg);
			SSAargs.add(SSAArg);
			position++;
		}
		
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Call, 
				SSATarget, null, new SSACall(exp.getMethod(), SSAargs));
		body.add(ret);
		return ret;
	}
	
	/*
	@Override
	public Object visit(Exp exp) {
	}
	 */
	
	@Override
	public Object visit(ExpStatement exp) {
		return exp.getExp().accept(this);
	}
	
	@Override
	public Object visit(IfStatement exp) {
		 
		int ifID = exp.hashCode();
 		String elseLabel = "lif_"+ifID+"_else";
 		String doneLabel = "lif_"+ifID+"_done";
		
		// compile condition, add nbranch statement
		body.add(new SSAStatement(exp, SSAStatement.Op.NBranch,
			(SSAStatement)exp.getCondition().accept(this), null, elseLabel));

	
		Set<String> newAssigns = new HashSet<String>();

 		
 		// save the old localVarsPreAssign table and replace it with a new one that's
 		// empty. this new table will keep the old SSA-pointers of the variables that
 		// get assigned new values inside this while-loop
 		Map<String, SSAStatement> prevLocalVarsPreAssign = localVarsPreAssign;
		localVarsPreAssign = new HashMap<String, SSAStatement>();	
		
		
		// compile if part
		exp.getIfPart().accept(this);
		// add goto statement and else-label
		body.add(new SSAStatement(exp, SSAStatement.Op.Goto, doneLabel));
		body.add(new SSAStatement(exp, SSAStatement.Op.Label, elseLabel));
		
		// restore localVars with oldSSAs, move the new SSAs into a separate table
		Map<String, SSAStatement> newAssignsIf = new HashMap<String, SSAStatement>();
		for (Map.Entry<String, SSAStatement> entry : localVarsPreAssign.entrySet()) {
			
			String varName = entry.getKey();
			SSAStatement oldSSA = entry.getValue();
			SSAStatement newSSA = localVars.get(varName);
			
			localVars.put(varName, oldSSA);		// restore
			newAssignsIf.put(varName, newSSA);
			newAssigns.add(varName);
		}

		localVarsPreAssign.clear();

		// compile else part
		if (exp.getElsePart() != null) {
			exp.getElsePart().accept(this);
		}
		// add done-label
		body.add(new SSAStatement(exp, SSAStatement.Op.Label, doneLabel));
		
		
		// restore localVars with oldSSAs, move the new SSAs into a separate table
		Map<String, SSAStatement> newAssignsElse = new HashMap<String, SSAStatement>();
		for (Map.Entry<String, SSAStatement> entry : localVarsPreAssign.entrySet()) {
			
			String varName = entry.getKey();
			SSAStatement oldSSA = entry.getValue();
			SSAStatement newSSA = localVars.get(varName);
			
			localVars.put(varName, oldSSA);		// restore
			newAssignsElse.put(varName, newSSA);
			newAssigns.add(varName);
		}
		
		
		// move one level up: restore prevLocalVarsPreAssign to be the current one
		localVarsPreAssign = prevLocalVarsPreAssign;
		
		
		// for each var that was assigned something new in if or else...
		for (String varName : newAssigns) {
			
			SSAStatement newSSAIf = newAssignsIf.get(varName);
			SSAStatement newSSAElse = newAssignsElse.get(varName);
			
			// if null, replace with the oldSSA value
			if (newSSAIf==null) {
				newSSAIf = localVars.get(varName);
			} else if (newSSAElse==null) {
				newSSAElse = localVars.get(varName);
			}
			
			SSAStatement unifySSA = new SSAStatement(exp, SSAStatement.Op.Unify,
					newSSAIf, newSSAElse);
			body.add(unifySSA);
			
			updateLocalVarSSA(varName, unifySSA);
		}
		
		return null;
	}
	
	
	
	@Override
	public Object visit(IndexExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Index, 
				(SSAStatement)exp.getTarget().accept(this),
				(SSAStatement)exp.getIndex().accept(this));
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(IntLiteralExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Int, exp.getValue());
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(MemberExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Member, 
				(SSAStatement)exp.getSub().accept(this), null,
				exp.getMember());
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(NewIntArrayExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.NewIntArray, 
				(SSAStatement)exp.getSize().accept(this), null);
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(NewObjectExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.NewObj, exp.getName());
		body.add(ret);
		return ret;
	}
	
	@Override
	public Object visit(NotExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Not,
				(SSAStatement)exp.getSub().accept(this), null);
		body.add(ret);
		return ret;
	}

	/*
	compile(MethodDecl method) will visit the param nodes explicitly
	@Override
	public Object visit(Parameter exp) {
	}
	*/
	
	@Override
	public Object visit(PrintStatement exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.Print,
				(SSAStatement)exp.getValue().accept(this), null);
		body.add(ret);
		return ret;
	}
	
	/*
	@Override
	public Object visit(Statement exp) {
	}
	*/
	
	@Override
	public Object visit(ThisExp exp) {
		SSAStatement ret = new SSAStatement(exp, SSAStatement.Op.This);
		body.add(ret);
		return ret;
	}
	
	
	/* 
	@Override
	public Object visit(Type exp) {
	}
	*/
	
	
	// VarDecl nodes will be explictly visited by whomever are handling
	// methods and classes
	/*
	@Override
	public Object visit(VarDecl exp) {
	}
	*/
	
	// assuming this visit() is only called on RHS VarExp nodes
	@Override
	public Object visit(VarExp exp) {
		
		String varName = exp.getName();
		SSAStatement localVarSSA;
		SSAStatement ret = null;
		
		// look up this variable in the local vars table
		localVarSSA = localVars.get(varName);
		
		if (localVarSSA!=null) {
			ret = localVarSSA;
		}
		else {
			// assume it's a valid field of this class
			// add a This-SSAStatement and a Member-SSAStatement to the body
			SSAStatement thisSSA = new SSAStatement(exp, SSAStatement.Op.This);
			body.add(thisSSA);
			ret = new SSAStatement(exp, SSAStatement.Op.Member, thisSSA, null, varName);
			body.add(ret);
		}
		return ret;
	}
	


	@Override
	public Object visit(WhileStatement exp) {
 
 		int whileID = exp.hashCode();
 		String startLabel = "lwhile_"+whileID+"_start";
 		String endLabel = "lwhile_"+whileID+"_end";
 		
 		// while statements have 2 levels: condition and body.
 		
 		
 		// move one level down: now at level of condition
 		Map<String, SSAStatement> prevLocalVarsPreAssign = localVarsPreAssign;
		localVarsPreAssign = new HashMap<String, SSAStatement>();	
		
		
		// add while_start label
		body.add(new SSAStatement(exp, SSAStatement.Op.Label, startLabel));
		// compile condition
		SSAStatement condSSA = (SSAStatement)exp.getCondition().accept(this);
		
		
		// move another level down: now at level of body
		Map<String, SSAStatement> condVarsPreAssign = localVarsPreAssign;
		localVarsPreAssign = new HashMap<String, SSAStatement>();
				
		
		//add nbranch statement
		body.add(new SSAStatement(exp, SSAStatement.Op.NBranch,
				condSSA, null, endLabel));
		// compile while-loop body
		exp.getBody().accept(this);
		// add goto statement and while_end label
		body.add(new SSAStatement(exp, SSAStatement.Op.Goto, startLabel));
		body.add(new SSAStatement(exp, SSAStatement.Op.Label, endLabel));
		
		if (condVarsPreAssign.isEmpty()) {
			moveUp(exp, prevLocalVarsPreAssign);
		}
		else {
			moveUp(exp, condVarsPreAssign);
			moveUp(exp, prevLocalVarsPreAssign);
		}
		
		return null;
	}
	
	
	private static void moveUp(ASTNode ast, Map<String, SSAStatement> prevLocalVarsPreAssign) {

		// move one level up: restore condVarsPreAssign to be the current one
		Map<String, SSAStatement> oldSSAs = localVarsPreAssign;
		localVarsPreAssign = prevLocalVarsPreAssign;


		// for each var in nextLocalVarsPreAssign
		for (Map.Entry<String, SSAStatement> entry : oldSSAs.entrySet()) {
	
			String varName = entry.getKey();
			SSAStatement oldSSA = entry.getValue();
	
			// generate a unify-SSAStatement with its old SSA from bodyVarsPreAssign
			// and its new SSA from localVars
			SSAStatement unifySSA = new SSAStatement(ast, SSAStatement.Op.Unify,
					oldSSA, localVars.get(varName));
			body.add(unifySSA);
	
			// restore localVars with the old SSAs from localVarsPreAssign
			localVars.put(varName, oldSSA);
	
			// update the localVars and localVarsPreAssign at this level
			updateLocalVarSSA(varName, unifySSA);
		}
	}
	 
	
	/*
	// move one level up: restore condVarsPreAssign to be the current one
	Map<String, SSAStatement> bodyVarsPreAssign = localVarsPreAssign;
	localVarsPreAssign = condVarsPreAssign;

	
	// for each var in nextLocalVarsPreAssign
	for (Map.Entry<String, SSAStatement> entry : bodyVarsPreAssign.entrySet()) {
	
		String varName = entry.getKey();
		SSAStatement oldSSA = entry.getValue();
	
		// generate a unify-SSAStatement with its old SSA from bodyVarsPreAssign
		// and its new SSA from localVars
		SSAStatement unifySSA = new SSAStatement(exp, SSAStatement.Op.Unify,
				oldSSA, localVars.get(varName));
		body.add(unifySSA);
	
		// restore localVars with the old SSAs from localVarsPreAssign
		localVars.put(varName, oldSSA);
	
		// update the localVars and localVarsPreAssign at this level
		updateLocalVarSSA(varName, unifySSA);
	}

	// move another level up: restore prevLocalVarsPreAssign to be the current one
	condVarsPreAssign = localVarsPreAssign;
	localVarsPreAssign = prevLocalVarsPreAssign;

	// for each var in condVarsPreAssign
	for (Map.Entry<String, SSAStatement> entry : condVarsPreAssign.entrySet()) {
	
		String varName = entry.getKey();
		SSAStatement oldSSA = entry.getValue();
	
		// generate a unify-SSAStatement with its old SSA from bodyVarsPreAssign
		// and its new SSA from localVars
		SSAStatement unifySSA = new SSAStatement(exp, SSAStatement.Op.Unify,
				oldSSA, localVars.get(varName));
		body.add(unifySSA);
	
		// restore localVars with the old SSAs from localVarsPreAssign
		localVars.put(varName, oldSSA);
	
		// update the localVars and localVarsPreAssign at this level
		updateLocalVarSSA(varName, unifySSA);
	}*/
}
