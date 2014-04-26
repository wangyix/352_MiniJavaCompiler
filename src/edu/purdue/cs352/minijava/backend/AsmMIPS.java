package edu.purdue.cs352.minijava.backend;

import java.util.*;

import edu.purdue.cs352.minijava.ssa.*;
import edu.purdue.cs352.minijava.types.*;

public class AsmMIPS {
	StringBuilder sb;

	int wordSize = 4;
	
	// added by me
	int numSpills;
	int numCallerSavedRegs;


	// registers for MIPS:
	private static final String[] registers = {
		"zero",
		"at",
		"v0", "v1",
		"a0", "a1", "a2", "a3",
		"t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
		"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
		"t8", "t9",
		"k0", "k1",
		"gp", "sp", "fp", "ra"
	};

	// registers free for normal use
	private static final int[] freeRegisters = {
		4, 5, 6, 7, // a*
		8, 9, 10, 11, 12, 13, 14, 15, // t*
		16, 17, 18, 19, 20, 21, 22, 23, // s*
		24, 25 // t*
	};

	// pinned registers for arguments:
	private static final int[] argRegisters = {
		4, 5, 6, 7
	};

	// mapping of arg register indexes to free register indexes
	private static final int[] argFreeRegisters = {
		0, 1, 2, 3
	};

	// callee-saved registers, excluding stack/base
	private static final int[] calleeSavedRegisters = {
		16, 17, 18, 19, 20, 21, 22, 23, // s*
		28, // gp
		31, // ra
	};

	// caller-saved registers, excluding stack/base
	private static final int[] callerSavedRegisters = {
		2, 3, // v*
		4, 5, 6, 7, // a*
		8, 9, 10, 11, 12, 13, 14, 15, 24, 25 // t*
	};

	private AsmMIPS(StringBuilder sb) {
		this.sb = sb;
	}

	public static String compile(SSAProgram prog) {
		AsmMIPS compiler = new AsmMIPS(new StringBuilder());

		// SPIM stuff
		compiler.sb.append(
			"main:\n" +
			" jal mj_main\n" +
			" li $v0, 10\n" +
			" syscall\n\n" +

			"minijavaNew:\n" +
			" move $t0, $a0\n" +
			" mul $a0, $a1, 4\n" +
			" li $v0, 9\n" +
			" syscall\n" +
			" sw $t0, ($v0)\n" +
			" j $ra\n\n" +

			"minijavaNewArray:\n" +
			" move $t0, $a0\n" +
			" mul $a0, $a0, 4\n" +
			" add $a0, $a0, 4\n" +
			" li $v0, 9\n" +
			" syscall\n" +
			" sw $t0, ($v0)\n" +
			" j $ra\n\n" +

			".data\n" +
			".align 4\n" +
			"minijavaNewline:\n" +
			" .asciiz \"\\n\"\n\n" +

			".text\n" +
			"minijavaPrint:\n" +
			" li $v0, 1\n" +
			" syscall\n" +
			" la $a0, minijavaNewline\n" +
			" li $v0, 4\n" +
			" syscall\n" +
			" j $ra\n\n"
		); 

		// first compile main
		compiler.compile(prog, prog.getMain(), "mj_main");

		// then compile all the classes
		for (SSAClass cl : prog.getClassesOrdered()) {
			compiler.compile(prog, cl);
		}

		return compiler.toString();
	}

	// compile this class
	private void compile(SSAProgram prog, SSAClass cl) {
		// first make the vtable for this class
		sb.append(".data\n.align ");
		sb.append(wordSize);
		sb.append("\n");
		sb.append("mj__v_" + cl.getASTNode().getName() + ":\n");
		
		// FILLIN
		
		// tell spim to store the addresses of vtable methods here
		ClassLayout.Vtable vtable = ClassLayout.getVtable(prog, cl);
		for (String methodName : vtable.methods) {
			
			sb.append(String.format(" .word mj__m_%s_%s\n",
					ClassLayout.getImplementor(prog, cl, methodName).getASTNode().getName(),
					methodName));
		}
		
		
		// now compile the actual methods
		sb.append(".text\n");
		for (SSAMethod m : cl.getMethodsOrdered()) {
			String name = "mj__m_" + cl.getASTNode().getName() + "_" + m.getMethod().getName();
			compile(prog, m, name);
		}
	}

	// compile this method with this name
	private void compile(SSAProgram prog, SSAMethod m, String name) {
		// beginning of the prologue
		sb.append(name);
		sb.append(":\n");
		sb.append(" add $sp, $sp, -");
		sb.append(wordSize);
		sb.append("\n");
		sb.append(" sw $fp, ($sp)\n");
		sb.append(" move $fp, $sp\n");

		// pin registers
		for (SSAStatement s : m.getBody()) {
			if (s.getOp() == SSAStatement.Op.Parameter) {
				int paramNum = ((Integer) s.getSpecial()).intValue();
				if (paramNum < argRegisters.length)
					s.pinRegister(argFreeRegisters[paramNum]);
			}

			if (s.getOp() == SSAStatement.Op.Arg) {
				int argNum = ((Integer) s.getSpecial()).intValue();
				if (argNum < argRegisters.length)
					s.pinRegister(argFreeRegisters[argNum]);
				else
					s.pinRegister(-1); // pin to -1 to do this at Call time
			}
		}

		// FILLIN: perform register allocation
		RegisterAllocator.alloc(m, freeRegisters.length);
		
		
		
		// find how many regs are used
		int maxReg = -1;
		for (SSAStatement ssa : m.getBody()) {
			if (ssa.getRegister() > maxReg)
				maxReg = ssa.getRegister();
		}
		final int numRegsUsed = maxReg+1;

		
		// FILLIN: figure out how much space we need to reserve for spills
		
		numSpills = 0;
		for (SSAStatement ssa : m.getBody()) {
			if (ssa.getOp()==SSAStatement.Op.Store) {
				int offset = (int)ssa.getSpecial();
				if (offset >= numSpills)
					numSpills = offset+1;
			}
		}
		
		// FILLIN: and perhaps any other space we need to reserve (saved registers?)
		
		// find number of caller-saved regs used including v0, v1
		if (numRegsUsed <= 12)
			numCallerSavedRegs = 2 + numRegsUsed;
		else if (numRegsUsed <=20)
			numCallerSavedRegs = 2 + 12;
		else
			numCallerSavedRegs = 2 + 12 + (numRegsUsed-20);
		
		
		// FILLIN: reserve space for spills, v0,v1, and caller-saved regs
		sb.append(" add $sp, $sp, -");
		sb.append(wordSize*(numSpills + numCallerSavedRegs));
		sb.append("\n");

		// FILLIN: save the callee-saved registers, anything else that needs to be saved
		
		// figure out how many of S0-S7 are used
		
		final int numCalleeSavedRegs = Math.min(Math.max(numRegsUsed-12, 0), 8);
		
		
		// allocate stack space and save S registers that are used
		for (int sIndex=0; sIndex<numCalleeSavedRegs; sIndex++) {
			sb.append(String.format(" add $sp, $sp, -%d\n", wordSize));
			sb.append(String.format(" sw $s%d, ($sp)\n", sIndex));
		}
		
		// allocate stack space and save ra
		sb.append(String.format(" add $sp, $sp, -%d\n", wordSize));
		sb.append(" sw $ra, ($sp)\n");
		
		
		// find highest number of args of any method calls
		int maxPosition = 0;
		for (SSAStatement ssa : m.getBody()) {
			if (ssa.getOp()==SSAStatement.Op.Arg) {
				int position = (int)ssa.getSpecial();
				if (position > maxPosition)
					maxPosition = position;
			}
		}
		final int argSpace = Math.max(maxPosition-3, 0);
		
		
		// allocate stack space for stack args of future method calls
		if (argSpace > 0)
			sb.append(String.format(" add $sp, $sp, -%d\n", argSpace*wordSize));
		
		
		// END PROLOGUE ===========================================================================
		

		// now write the code
		for (SSAStatement s : m.getBody()) {
			compile(prog, name, s);
		}

		
		
		// EPILOGUE ==============================================================================
		
		// the epilogue starts here
		sb.append(" .ret_");
		sb.append(name);
		sb.append(":\n");

		// FILLIN: restore the callee-saved registers (anything else?)
		
		// if there were stack args, unallocate the space for those from stack
		if (argSpace > 0)
			sb.append(String.format(" add $sp, $sp, %d\n", argSpace*wordSize));
		
		// restore ra
		sb.append(" lw $ra, ($sp)\n");
		sb.append(String.format(" add $sp, $sp, %d\n", wordSize));
		
		// restore S registers that were used
		for (int sIndex=numCalleeSavedRegs-1; sIndex>=0; sIndex--) {
			sb.append(String.format(" lw $s%d, ($sp)\n", sIndex));
			sb.append(String.format(" add $sp, $sp, %d\n", wordSize));
		}
		

		// and the rest of the epilogue
		sb.append(" move $sp, $fp\n");
		sb.append(" lw $fp, ($sp)\n");
		sb.append(" add $sp, $sp, ");
		sb.append(wordSize);
		sb.append("\n");
		sb.append(" j $ra\n");
	}

	
	
	// compile this statement (FILLIN: might need more registers, coming from above method)
	private void compile(SSAProgram prog, String methodName, SSAStatement s) {
		// recommended for debuggability:
		sb.append(" # ");
		if (s.getRegister() >= 0)
			sb.append(reg(s));
		sb.append(": ");
		sb.append(s.toString());
		sb.append("\n");
		

		switch (s.getOp()) {
		// FILLIN (this is the actual code generator!)
		case Unify:
		case Alias:
			break;
		case This: {
			// "this" is stored in v0
			sb.append(String.format(" move $%s, $v0\n", reg(s)));
			break;
		}
		case Parameter: {
			int position = (int)s.getSpecial();
			if (position < 4)
				break;
			// load stack param from below current frame
			sb.append(String.format(" lw $%s, %d($fp)\n", reg(s), (position-3)*wordSize));
			break;
		}
		case Arg: {
			int position = (int)s.getSpecial();
			SSAStatement value = s.getLeft();
			if (position < 4) {
				if (value.getRegister() != s.getRegister())
					sb.append(String.format(" move $%s, $%s\n", reg(s), reg(value)));
			} else {
				// store stack param starting at sp and go down stack
				sb.append(String.format(" sw $%s, %d($sp)\n", reg(value), (position-4)*wordSize));
			}
			break;
		}
		case Null: {
			// set register to 0
			sb.append(String.format(" move $%s, $zero\n", reg(s)));
			break;
		}
		case Int: {
			// load value into register
			int intValue = (int)s.getSpecial();
			sb.append(String.format(" li $%s, %d\n", reg(s), intValue));
			break;
		}
		case Boolean: {
			// load value into register (0 for false, 1 for true)
			boolean boolValue = (boolean)s.getSpecial();
			if (!boolValue)
				sb.append(String.format(" move $%s, $zero\n", reg(s)));
			else
				sb.append(String.format(" li $%s, 1\n", reg(s)));
			break;
		}
		case NewObj: {
			
			// save caller-saved regs except for the one the new obj pointer will be stored in
			appendCodeToSaveCallerSavedRegs(s.getRegister());
			
			// load address of relevant vtable into a0, load wordsize of object into a1
			String className = (String)s.getSpecial();
			sb.append(String.format(" la $a0, mj__v_%s\n", className));
			sb.append(String.format(" li $a1, %d\n",
					ClassLayout.objectSize(prog, prog.getClasses().get(className))));
			// call minijavaNew, copy addr of new object to assigned register
			sb.append(" jal minijavaNew\n");
			sb.append(String.format(" move $%s, $v0\n", reg(s)));
			
			
			// restore caller-saved regs except for the one the new obj pointer will be stored in
			appendCodeToLoadCallerSavedRegs(s.getRegister());
			break;
		}
		
		case NewIntArray: {
			
			// save caller-saved regs except for the one the new array pointer will be stored in
			appendCodeToSaveCallerSavedRegs(s.getRegister());
			
			// load wordsize of array into a0
			SSAStatement size = s.getLeft();
			if (size.getRegister() != 0)
				sb.append(String.format(" move $a0, $%s\n", reg(size)));
			// call minijavaNewArray, copy addr of new array to assigned register
			sb.append(" jal minijavaNewArray\n");
			sb.append(String.format(" move $%s, $v0\n", reg(s)));
			
			// restore caller-saved regs except for the one the new array pointer will be stored in
			appendCodeToLoadCallerSavedRegs(s.getRegister());
			break;
		}
		
		case Label: {
			sb.append(String.format(" .%s:\n", (String)s.getSpecial()));
			break;
		}
		case Goto: {
			sb.append(String.format(" j .%s\n", (String)s.getSpecial()));
			break;
		}
		case Branch: {
			SSAStatement condition = s.getLeft();
			sb.append(String.format(" bne $%s, $zero, .%s\n",
					reg(condition), (String)s.getSpecial()));
			break;
		}
		case NBranch: {
			SSAStatement condition = s.getLeft();
			sb.append(String.format(" beq $%s, $zero, .%s\n",
					reg(condition), (String)s.getSpecial()));
			break;
		}
		
		case Call: {
			// save caller-saved regs except the one the result will be stored in
			appendCodeToSaveCallerSavedRegs(s.getRegister());
			
			// move target obj ptr to v0
			SSAStatement target = s.getLeft();
			sb.append(String.format(" move $v0, $%s\n", reg(target)));
			
			// move method ptr (from vtable) to v1
			
			String targetTypeName = ((ObjectType)target.getType()).getName();
			String callMethodName = ((SSACall)s.getSpecial()).getMethod();
			
			ClassLayout.Vtable targetVtable = ClassLayout.getVtable(
					prog, prog.getClasses().get(targetTypeName));
			int methodOffset = targetVtable.methodOffsets.get(callMethodName);
			
			sb.append(" lw $v1, ($v0)\n");	// load ptr to vtable into v1
			sb.append(String.format(" lw $v1, %d($v1)\n", methodOffset*wordSize));
			
			// jump to method, move result to assigned register
			sb.append(" jal $v1\n");
			sb.append(String.format(" move $%s, $v0\n", reg(s)));
			
			// restore the caller-saved regs except the one the result will be stored in
			appendCodeToLoadCallerSavedRegs(s.getRegister());
			break;
		}
		
		case Print: {
			// save all caller-saved regs
			appendCodeToSaveCallerSavedRegs(-1);
			
			// move print value to a0
			SSAStatement printValue = s.getLeft();
			if (printValue.getRegister() != 0)
				sb.append(String.format(" move $a0, $%s\n", reg(printValue)));
			sb.append(" jal minijavaPrint\n");
			
			// restore all caller-saved regs
			appendCodeToLoadCallerSavedRegs(-1);
			break;
		}
		case Return: {
			// move return value to v0, jump to return code
			SSAStatement retValue = s.getLeft();
			sb.append(String.format(" move $v0, $%s\n", reg(retValue)));
			sb.append(String.format(" j .ret_%s\n", methodName));
			break;
		}
		case Member: {
			// find member offset
			SSAStatement target = s.getLeft();
			String memberName = (String)s.getSpecial();
			int memberOffset = getMemberOffset(prog, target, memberName);
			
			// load member value into assigned reg
			sb.append(String.format(" lw $%s, %d($%s)\n", reg(s),
					memberOffset*wordSize, reg(target)));
			break;
		}
		
		case Index: {
			SSAStatement target = s.getLeft();
			SSAStatement index = s.getRight();
			appendCodeToPutArrayElementPtrInV1(target, index);
			
			// load element into assigned reg
			sb.append(String.format(" lw $%s, ($v1)\n", reg(s)));
			break;
		}
		
		case Store: {
			SSAStatement storeValue = s.getLeft();
			int frameOffset = 1 + (int)s.getSpecial();
			sb.append(String.format(" sw $%s, -%d($fp)\n", reg(storeValue), frameOffset*wordSize));
			break;
		}
		case Load: {
			int frameOffset = 1 + (int)s.getSpecial();
			sb.append(String.format(" lw $%s, -%d($fp)\n", reg(s), frameOffset*wordSize));
			break;
		}
		
		case VarAssg: {
			SSAStatement assgValue = s.getLeft();
			if (s.getRegister() != assgValue.getRegister())
				sb.append(String.format(" move $%s, $%s\n", reg(s), reg(assgValue)));
			break;
		}
		case MemberAssg: {
			// find member offset
			SSAStatement target = s.getLeft();
			String memberName = (String)s.getSpecial();
			int memberOffset = getMemberOffset(prog, target, memberName);
			
			SSAStatement assgValue = s.getRight();
			sb.append(String.format(" sw $%s, %d($%s)\n", reg(assgValue),
					memberOffset*wordSize, reg(target)));
			break;
		}
		case IndexAssg: {
			SSAStatement target = s.getLeft();
			SSAStatement index = (SSAStatement)s.getSpecial();
			appendCodeToPutArrayElementPtrInV1(target, index);
			
			// store value at array element address
			SSAStatement assgValue = s.getRight();
			sb.append(String.format(" sw $%s, ($v1)\n", reg(assgValue)));
			break;
		}
		
		case Not: {
			sb.append(String.format(" seq $%s, $zero, $%s\n", reg(s), reg(s.getLeft())));
			break;
		}
		case Lt: {
			sb.append(String.format(" slt $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Le: {
			sb.append(String.format(" sle $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Eq: {
			sb.append(String.format(" seq $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Ne: {
			sb.append(String.format(" sne $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Gt: {
			sb.append(String.format(" sgt $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Ge: {
			sb.append(String.format(" sge $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case And: {
			// add operands together, set if result is 2
			sb.append(String.format(" add $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			sb.append(String.format(" seq $%s, $%s, 2\n", reg(s), reg(s)));
			break;
		}
		case Or: {
			// add operands together, set if result is not 0
			sb.append(String.format(" add $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			sb.append(String.format(" sne $%s, $%s, $zero\n", reg(s), reg(s)));
			break;
		}
		case Plus: {
			sb.append(String.format(" add $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Minus: {
			sb.append(String.format(" sub $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Mul: {
			sb.append(String.format(" mul $%s, $%s, $%s\n", reg(s),
					reg(s.getLeft()), reg(s.getRight())));
			break;
		}
		case Div: {
			sb.append(String.format(" div $%s, $%s\n", reg(s.getLeft()), reg(s.getRight())));
			sb.append(String.format(" mflo $%s\n", reg(s)));
			break;
		}
		case Mod: {
			sb.append(String.format(" div $%s, $%s\n", reg(s.getLeft()), reg(s.getRight())));
			sb.append(String.format(" mfhi $%s\n", reg(s)));
			break;
		}
		
		
		default:
			throw new Error("Implement MIPS compiler for " + s.getOp() + "!");
		}
	}

	private void appendCodeToPutArrayElementPtrInV1(SSAStatement target, SSAStatement index) {
		// calculate byte offset, put result in v1.
		sb.append(String.format(" mul $v1, $%s, %d\n", reg(index), wordSize));
		// add wordsize to that to account for length being stored first in array
		sb.append(String.format(" add $v1, $v1, %d\n", wordSize));
		// add the array address to that to get address of the element
		sb.append(String.format(" add $v1, $v1, $%s\n", reg(target)));
	}
	
	private int getMemberOffset(SSAProgram prog, SSAStatement target, String memberName) {
		String targetTypeName = ((ObjectType)target.getType()).getName();
		if (targetTypeName.equals("int[]") && memberName.equals("length"))
			return 0;
		SSAClass targetTypeClass = prog.getClasses().get(targetTypeName);
		return ClassLayout.fieldOffset(prog, targetTypeClass, memberName);
	}
	
	private void appendCodeToSaveCallerSavedRegs(int resultFreeReg) {
		int resultReg = resultFreeReg < 0 ? -1 : freeRegisters[resultFreeReg];
		int frameOffset = 1 + numSpills;
		for (int i=0; i<numCallerSavedRegs; i++) {
			if (callerSavedRegisters[i]!=resultReg) {
				sb.append(String.format(" sw $%s, -%d($fp)\n",
						registers[callerSavedRegisters[i]], frameOffset*wordSize));
				frameOffset++;
			}
		}
	}
	private void appendCodeToLoadCallerSavedRegs(int resultFreeReg) {
		int resultReg = resultFreeReg < 0 ? -1 : freeRegisters[resultFreeReg];
		int frameOffset = 1 + numSpills;
		for (int i=0; i<numCallerSavedRegs; i++) {
			if (callerSavedRegisters[i]!=resultReg) {
				sb.append(String.format(" lw $%s, -%d($fp)\n",
						registers[callerSavedRegisters[i]], frameOffset*wordSize));
				frameOffset++;
			}
		}
	}
	
	private String reg(SSAStatement s) {
		return registers[freeRegisters[s.getRegister()]];
	}
	
	// get the actual code generated
	public String toString() {
		return sb.toString();
	}
}