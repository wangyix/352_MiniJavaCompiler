package edu.purdue.cs352.minijava.backend;

import java.util.*;

import edu.purdue.cs352.minijava.ssa.*;

public class RegisterAllocator {
	/* a "variable" is just a set of SSA statements. We alias it (sort of) to
	 * make this explicit. A Variable is thus equivalently a definition of
	 * def(v). */
	static class Variable implements Comparable<Variable> {		
		private final SSAStatement master; // just for debugging
		private static int highestRegisterUsed = -1;
		
		private Set<SSAStatement> v;
		private boolean registerPinned;
		private int register; 
		
		private Variable(SSAStatement s) {
			master = s;
			registerPinned = s.registerPinned();
			register = s.getRegister();
			v = new HashSet<SSAStatement>();
			v.add(s);
		}
		@Override
		public int compareTo(Variable var) {
			return master.getIndex() - var.master.getIndex();
		}
	}


	// a node in the control flow graph
	class CFNode {
		private Set<CFNode> pred;
		private Set<CFNode> succ;
		
		private Variable def;
		private Set<Variable> use;
		private Set<Variable> in;
		private Set<Variable> succIn;
		
		private CFNode(SSAStatement source) {
			pred = new HashSet<CFNode>();
			succ = new HashSet<CFNode>();
			def = null;
			use = new HashSet<Variable>();
			in = new HashSet<Variable>();
			succIn = new HashSet<Variable>();
			
			// unify CFNodes do not define anything since they should act
			// like pass-throughs in the cfgraph.
			// all other ssa types define themselves
			if (source.getOp() != SSAStatement.Op.Unify) {
				Variable defVar = ssaToVarMap.get(source);
				def = defVar;
			}
		}
   }

	// a node in the interference graph (a temporary)
	class TempNode {
		// FILLIN...
		private Variable var;
		private Set<TempNode> adj;
		private int degree;
		private int color;
		private TempNode(Variable var) {
			this.var = var;
			adj = new HashSet<TempNode>();
			degree = 0;
			color = 0;
		}
	}

	
	// the block we're performing allocation over
	private List<SSAStatement> block;
	
	private Map<String, SSAStatement> labelToSsaMap;
	
	private Set<Variable> variables;
	private Map<SSAStatement, Variable> ssaToVarMap;
	
	private List<CFNode> cfnodes;
	private Map<SSAStatement, CFNode> ssaToCfnodeMap;
	
	private Set<TempNode> tempnodes;
	private Map<Variable, TempNode> varToTempnodeMap;
	
	
	
	private RegisterAllocator(List<SSAStatement> block) {
		this.block = block;
		labelToSsaMap = new HashMap<String, SSAStatement>();
		variables = new HashSet<Variable>();
		ssaToVarMap = new HashMap<SSAStatement, Variable>();
		cfnodes = new ArrayList<CFNode>();
		ssaToCfnodeMap = new HashMap<SSAStatement, CFNode>();
		tempnodes = new HashSet<TempNode>();
		varToTempnodeMap = new HashMap<Variable, TempNode>();
		// build labelToSsaMap
		for (SSAStatement ssa : block) {
			if (ssa.getOp() == SSAStatement.Op.Label) {
				labelToSsaMap.put((String)ssa.getSpecial(), ssa);
			}
		}
	}

	// perform all register allocations for this program
	public static void alloc(SSAProgram prog, int freeRegisters) {
		// first main
		SSAMethod main = prog.getMain();
		main.setBody(alloc(main.getBody(), freeRegisters));

		// then each class
		for (SSAClass cl : prog.getClassesOrdered())
			alloc(cl, freeRegisters);
	}

	// perform all register allocations for this class
	public static void alloc(SSAClass cl, int freeRegisters) {
		for (SSAMethod m : cl.getMethodsOrdered())
			alloc(m, freeRegisters);
	}

	// perform register allocation for this method
	public static void alloc(SSAMethod m, int freeRegisters) {
		m.setBody(alloc(m.getBody(), freeRegisters));
	}

	// the register allocator itself
	public static List<SSAStatement> alloc(List<SSAStatement> block, int freeRegisters) {
		Set<TempNode> actualSpills;

		RegisterAllocator ra = new RegisterAllocator(block);

		while (true) {
			
			ra.init();
				
			// prefill the variables with single statements
			ra.initVariables();

			// unify
			ra.unifyVariables();

			// now build the CF nodes
			ra.initCFNodes();

			// build the use[n] relationship from them
			// marks each variable as used in the program or not
			ra.addUses();


			//System.out.println("\nliveness analysis...");
			
			// build their successor/predecessor relationships
			ra.cfPredSucc();

			// liveness analysis
			ra.liveness();
			//ra.printCfg();

			
			//System.out.println("\nallocating registers...");
			
			// build the temporaries
			ra.initTempNodes();
			
			// and figure out their interference
			ra.buildInterference();

			// do we need to spill?
			actualSpills = ra.select(freeRegisters);
			
			
			if (actualSpills.isEmpty())
				break;

			// OK, rewrite to perform the spills
			ra.performSpills(actualSpills);
		}

		return ra.block;
	}


	private void init() {
		labelToSsaMap.clear();
		variables.clear();
		ssaToVarMap.clear();
		cfnodes.clear();
		ssaToCfnodeMap.clear();
		tempnodes.clear();
		varToTempnodeMap.clear();
		Variable.highestRegisterUsed = -1;
		
		// clear nonpinned ssa's assigned register to -1
		for (SSAStatement ssa : block) {
			if (!ssa.registerPinned())
				ssa.setRegister(-1);
		}
		
		// build labelToSsaMap
		for (SSAStatement ssa : block) {
			if (ssa.getOp() == SSAStatement.Op.Label) {
				labelToSsaMap.put((String)ssa.getSpecial(), ssa);
			}
		}
		// reset the right of all VarAssg back to null
		for (SSAStatement ssa : block) {
			if (ssa.getOp() == SSAStatement.Op.VarAssg)
				ssa.setRight(null);
		}
	}

	
	private void initVariables() {
		// Make a variable for each SSAStatement
		for (SSAStatement ssa : block) {
			Variable newVar = new Variable(ssa);
			variables.add(newVar);
			ssaToVarMap.put(ssa, newVar);
		}
	}
	
	
	private void unifyVariables() {
		// find all Unify SSAStatements, join variables together
		for (SSAStatement ssa : block) {
			if (ssa.getOp() == SSAStatement.Op.Unify) {
				// unify the variables of the left, right, and the
				// unify statement itself into the left variable
				Variable leftVar = ssaToVarMap.get(ssa.getLeft());
				Variable rightVar = ssaToVarMap.get(ssa.getRight());
				Variable unifyVar = ssaToVarMap.get(ssa);
				unify(unifyVar, leftVar);
				unify(unifyVar, rightVar);
			}
		}
	}
	private void unify(Variable leftVar, Variable rightVar) {
		// reassign right variable's SSAStatements to left variable
		// if either one is pinned to a register, left will be pinned to it
		// remove the right variable from variables list
		if (leftVar != rightVar) {
			leftVar.v.addAll(rightVar.v);
			for (SSAStatement rightSSA : rightVar.v) {
				ssaToVarMap.put(rightSSA, leftVar);
			}
			if (rightVar.registerPinned) {
				if (!leftVar.registerPinned) {
					leftVar.register = rightVar.register;
					leftVar.registerPinned = true;
				} else if (leftVar.register != rightVar.register) {
					throw new Error("Attempted to unify "
							+"two variables pinned to different registers.");
				}
			}
			variables.remove(rightVar);
		}
	}
	
	
	
	private void initCFNodes() {
		// create a CFNode for every SSAStatement
		for (SSAStatement ssa : block) {
			CFNode newCfnode = new CFNode(ssa);
			cfnodes.add(newCfnode);
			ssaToCfnodeMap.put(ssa, newCfnode);
		}
	}
	
	
	private void addUses() {
		
		List<SSAStatement> usedSsas = new ArrayList<SSAStatement>();
		
		for (SSAStatement ssa : block) {
						
			// find the ssas used by this ssa
			
			usedSsas.clear();
			
			// special cases:			
			switch (ssa.getOp()) {
			case Call:
				SSACall call = (SSACall)ssa.getSpecial();
				for (SSAStatement arg : call.getArgs()) {
					usedSsas.add(arg);
				}
				break;
			case IndexAssg:
				usedSsas.add((SSAStatement)ssa.getSpecial());
				break;
			case Unify:
			case Alias:
				// unify and alias do not actually use anything by themselves.
				continue;
			default:
			}
			
			// add the left and right to the used ssas
			if (ssa.getLeft() != null) {
				usedSsas.add(ssa.getLeft());
			}
			if (ssa.getRight() != null) {
				usedSsas.add(ssa.getRight());
			}
			
			// add the variables of these used ssas
			// to the use[n] of this ssa's cfnode
			// mark these variables as being used somewhere in the program
			CFNode node = ssaToCfnodeMap.get(ssa);
			for (SSAStatement usedSsa : usedSsas) {
				Variable usedVar = ssaToVarMap.get(usedSsa);
				node.use.add(usedVar);
				node.in.add(usedVar);
			}
		}
	}
	
	
	
	private void cfPredSucc() {
		
		List<CFNode> succNodes = new ArrayList<CFNode>();
		
		for (int i=0; i<block.size(); i++) {
			
			succNodes.clear();
			SSAStatement ssa = block.get(i);
			
			// if op is Goto, Branch, or NBranch, add its label's
			// CFNode as a successor
			switch (ssa.getOp()) {
			case Goto: 
			case Branch:
			case NBranch: {
				SSAStatement labelSsa = labelToSsaMap.get(
						(String)ssa.getSpecial());
				CFNode succNode = ssaToCfnodeMap.get(labelSsa);
				succNodes.add(succNode);
				break;
			}
			default:
			}
			
			// if op is not a Goto, add the CFNode of the next ssa in the block
			// as a successor (if there is a next ssa)
			if (ssa.getOp() != SSAStatement.Op.Goto
					&& i < block.size()-1) {
				SSAStatement nextSsa = block.get(i+1);
				succNodes.add(ssaToCfnodeMap.get(nextSsa));
			}
			
			// add the successors to this CFNode, add this CFNode to the
			// predecessors of those successors
			CFNode node = ssaToCfnodeMap.get(ssa);
			for (CFNode succNode : succNodes) {
				node.succ.add(succNode);
				succNode.pred.add(node);
			}
		}
	}
	
	
	
	private void liveness() {
		
		boolean cfgHasChanged;
		do {
			cfgHasChanged = false;
			
			for (int i=cfnodes.size()-1; i>=0; i--) {
				
				CFNode node = cfnodes.get(i);
				boolean nodeHasChanged = false;
								
				// add in[s] of all successors s to succIn[n]
				
				for (CFNode successor : node.succ) {
					if (node.succIn.addAll(successor.in)) {
						nodeHasChanged = true;
					}
				}
				
				// assumption: in[n] already contains use[n]
				
				// update in[n] if out[n] has changed
				if (nodeHasChanged) {
					for (Variable outVar : node.succIn) {
						if (node.def != outVar) {
							node.in.add(outVar);
						}
					}					
					cfgHasChanged = true;
				}
			}
		} while (cfgHasChanged);
	}
	
	
	private void initTempNodes() {
		// create a tempnode for each variable
		for (Variable var : variables) {
			TempNode newTempnode = new TempNode(var);
			tempnodes.add(newTempnode);
			varToTempnodeMap.put(var, newTempnode);
		}
	}
	
	
	private void buildInterference() {
		// for each cfnode, add edges to interference graph according to
		// succIn[n], which is the union of in[s] of all its successors
		Set<Variable> succInCopy = new HashSet<Variable>();
		for (CFNode cfnode : cfnodes) {
			// make a copy of succIn[n]
			succInCopy.clear();
			succInCopy.addAll(cfnode.succIn);
			
			// at each iteration, remove an anchor node from succIn[n] and
			// connect that node to all remaining nodes in in[n] with an edge
			for (Variable anchorVar : cfnode.succIn) {
				TempNode anchorNode = varToTempnodeMap.get(anchorVar);
				succInCopy.remove(anchorVar);
				for (Variable var : succInCopy) {
					TempNode node = varToTempnodeMap.get(var);
					anchorNode.adj.add(node);
					anchorNode.degree++;
					node.adj.add(anchorNode);
					node.degree++;
				}
			}
			
			// connect node of def[n] to all nodes in succIn[n] if needed
			if (cfnode.def!=null && !cfnode.succIn.contains(cfnode.def)) {
				TempNode anchorNode = varToTempnodeMap.get(cfnode.def);
				for (Variable var : cfnode.succIn) {
					TempNode node = varToTempnodeMap.get(var);
					anchorNode.adj.add(node);
					anchorNode.degree++;
					node.adj.add(anchorNode);
					node.degree++;
				}
			}
		}
	}
	
	
	private Set<TempNode> select(int numRegisters) {
		
		Set<TempNode> noRegisterNodes = new HashSet<TempNode>();
		Set<TempNode> pinnedNodes = new HashSet<TempNode>();
		Stack<TempNode> nonSpillNodes = new Stack<TempNode>();
		Set<TempNode> potentialSpillNodes = new HashSet<TempNode>();
		
		int nodesRemaining = tempnodes.size();

		
		// remove all pinned nodes unconditionally (including those pinned to no register)
		for (TempNode node : tempnodes) {
			if (node.var.registerPinned) {
				node.color = -1;
				for (TempNode adjNode : node.adj){
					adjNode.degree--;
				}
				if (node.var.register==-1)
					noRegisterNodes.add(node);
				else
					pinnedNodes.add(node);
				nodesRemaining--;
			}
		}
	
		
		// remove nodes from graph one at a time, marking potential spills
		while (nodesRemaining > 0) {
			
			// try to remove a tempnode with degree less than numRegisters
			boolean nonSpillNodeFound = false;
			for (TempNode node : tempnodes) {
				if (node.color!=-1 && node.degree<numRegisters) {					
					node.color = -1;	// mark as removed from graph
					for (TempNode adjNode : node.adj){
						adjNode.degree--;
					}
					nonSpillNodes.push(node);
					nonSpillNodeFound = true;
					break;
				}
			}
			
			// if no such tempnode exists, remove the node with the highest
			// degree and mark it as a potential spill
			if (!nonSpillNodeFound) {
				int maxDegree = -1;
				TempNode maxNode = null;
				for (TempNode node : tempnodes) {
					if (node.color!=-1 && node.degree>maxDegree) {
						maxNode = node;
						maxDegree = node.degree;
					}
				}
				maxNode.color = -1;
				for (TempNode adjNode : maxNode.adj){
					adjNode.degree--;
				}
				potentialSpillNodes.add(maxNode);
			}
			
			nodesRemaining--;
		}
		
		
		
		// for nodes pinned to no registers, assign them register -1
		// DO NOT add them back to the graph (leave their color as -1)
		for (TempNode node : noRegisterNodes) {
			setRegister(node.var, -1);
		}
		
		
		// add nodes pinned to registers, assigning each the register that
		// its var is assigned
		for (TempNode node : pinnedNodes) {
			node.color = node.var.register;
			setRegister(node.var, node.var.register);
		}
		
		
		Set<Integer> adjColors = new HashSet<Integer>();
		
		// add nonspill nodes back, assigning colors to each
		//for (TempNode node : nonSpillNodes) {
		while (!nonSpillNodes.isEmpty()) {
			
			TempNode node = nonSpillNodes.pop();
			
			// set this node's color to the lowest value that's not the color
			// of any of its adjacent nodes
			adjColors.clear();
			for (TempNode adjNode : node.adj) {
				if (adjNode.color != -1)
					adjColors.add(adjNode.color);
			}
			/*
			System.out.print(node.var.master.getIndex()+" adj with");
			for (TempNode adjNode : node.adj) {
				System.out.print(" "+adjNode.var.master.getIndex());
			}
			System.out.print("\n"+node.var.master.getIndex()+" is currently adj with colors");
			for (int i : adjColors)
					System.out.print(" "+i);
					*/

			for (int i=0; i<numRegisters; i++) {
				if (!adjColors.contains(i)) {
					node.color = i;
					setRegister(node.var, i);
					//System.out.println(", will be assigned color "+i);
					break;
				}
			}
		}
		
		// add potential spill nodes back.  If we get to a point where non can
		// be added, we have actual spills
		while (!potentialSpillNodes.isEmpty()) {
			boolean nodeAdded = false;
			for (TempNode node : potentialSpillNodes) {
				adjColors.clear();
				for (TempNode adjNode : node.adj) {
					if (adjNode.color != -1)
						adjColors.add(adjNode.color);
				}
				for (int i=0; i<numRegisters; i++) {
					if (!adjColors.contains(i)) {
						node.color = i;
						potentialSpillNodes.remove(node);
						nodeAdded = true;
						setRegister(node.var, i);
						break;
					}
				}
				if (nodeAdded)
					break;
			}
			if (!nodeAdded)
				break;
		}

		//System.out.println((Variable.highestRegisterUsed+1)+" registers used, "
				//+potentialSpillNodes.size()+" actual spills.");

		// actual spills are the remaining potentialspills that we couldn't
		// add back in
		Set<TempNode> actualSpills = new HashSet<TempNode>();		
		actualSpills.addAll(potentialSpillNodes);
		return actualSpills;
	}
	
	private void setRegister(Variable var, int reg) {
		for (SSAStatement ssa : var.v) {
			
			ssa.setRegister(reg);
			
			// set the register of each ssa of this variable if it defines
			// something that is used later
			/*
			CFNode node = ssaToCfnodeMap.get(ssa);
			if (node.succIn.contains(node.def)) {
				ssa.setRegister(reg);
			} //else {
				// all ssas that don't need to store their value
				// gets assigned 0 FOR NOW
				//ssa.setRegister(0);
			//}
			*/
		}
		
		if (reg > Variable.highestRegisterUsed)
			Variable.highestRegisterUsed = reg;
	}
	
	
	
	private void performSpills(Set<TempNode> actualSpills) {
		
		// find the lowest unused stack offset that's not already being used
		int lowestUnusedStackOffset = 0;
		for (SSAStatement ssa : block) {
			if (ssa.getOp() == SSAStatement.Op.Store) {
				int offset = (int)ssa.getSpecial();
				if (offset >= lowestUnusedStackOffset)
					lowestUnusedStackOffset = offset + 1;
			}
		}	
		
		// get spilled variables from spill nodes, assign each a unique
		// stack offset starting at lowestUnusedStackOffset
		Map<Variable, Integer> varToOffset = new HashMap<Variable, Integer>();
		int stackOffset = lowestUnusedStackOffset;
		for (TempNode node : actualSpills) {
			varToOffset.put(node.var, stackOffset);
			stackOffset++;
		}
		
		// go through block, insert stores and loads
		ListIterator<SSAStatement> blockIter = block.listIterator();
		while (blockIter.hasNext()) {
			
			SSAStatement ssa = blockIter.next();
			CFNode node = ssaToCfnodeMap.get(ssa);
			
			// add load instructions before this ssa for each spilled var
			// it uses, rewrite the ssa to reference the loads instead
			blockIter.previous();

			SSAStatement left = ssa.getLeft();
			if (left != null) {
				Integer offset = varToOffset.get(ssaToVarMap.get(left));
				if (offset != null) {
					SSAStatement loadSsa = new SSAStatement(null,
							SSAStatement.Op.Load, offset.intValue());
					blockIter.add(loadSsa);
					ssa.setLeft(loadSsa);
				}
			}
			SSAStatement right = ssa.getRight();
			if (right != null) {
				Integer offset = varToOffset.get(ssaToVarMap.get(right));
				if (offset != null) {
					SSAStatement loadSsa = new SSAStatement(null,
							SSAStatement.Op.Load, offset.intValue());
					blockIter.add(loadSsa);
					ssa.setRight(loadSsa);
				}
			}
				
			SSAStatement.Op op = ssa.getOp();
			if (op==SSAStatement.Op.Call) {
				List<SSAStatement> args = ((SSACall)ssa.getSpecial()).getArgs();
				for (int i=0; i<args.size(); i++) {
					SSAStatement arg = args.get(i);
					Integer offset = varToOffset.get(ssaToVarMap.get(arg));
					if (offset != null) {
						SSAStatement loadSsa = new SSAStatement(null,
								SSAStatement.Op.Load, offset.intValue());
						blockIter.add(loadSsa);
						args.set(i, loadSsa);
					}
				}
			} else if (op==SSAStatement.Op.IndexAssg) {
				SSAStatement index = (SSAStatement)ssa.getSpecial();
				Integer offset = varToOffset.get(ssaToVarMap.get(index));
				if (offset != null) {
					SSAStatement loadSsa = new SSAStatement(null,
							SSAStatement.Op.Load, offset.intValue());
					blockIter.add(loadSsa);
					ssa.setSpecial(loadSsa);
				}
			}
			
			blockIter.next();
			
			// add store instructions after this ssa if it defines a spilled var
			// and that var is used after this node
			if (node.def!=null && varToOffset.containsKey(node.def)
					&& node.succIn.contains(node.def)) {
				blockIter.add(new SSAStatement(null, SSAStatement.Op.Store,
						ssa, null, varToOffset.get(node.def)));
			}			
		}
	}
	
	
	
	
	
	// for testing!!!
	private void printCfg() {
		System.out.format("\n%-50s%-40s%-40s\n", "SSA", "in[n]", "union of in[s]");
		for (SSAStatement ssa : block) {
			CFNode node = ssaToCfnodeMap.get(ssa);
			System.out.format("%-50s", ssa.toString());
			System.out.format("%-40s", varSetToString(node.in));
			System.out.format("%-40s\n", varSetToString(node.succIn));
		}
	}
	
	private String varSetToString(Set<Variable> varSet) {
		// sort varset
		Variable[] vars = varSet.toArray(new Variable[0]);
		Arrays.sort(vars);
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Variable var : vars) {
			if (!first)
				sb.append(", ");
			sb.append(var.master.getIndex());
			first = false;
		}
		return sb.toString();
	}
}
