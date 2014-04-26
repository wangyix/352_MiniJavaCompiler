package edu.purdue.cs352.minijava.ssa;

import edu.purdue.cs352.minijava.ast.ASTNode;
import edu.purdue.cs352.minijava.types.StaticType;

public class SSAStatement {
    public static enum Op {
        // Meta:
        Unify,      // unify two SSAStatements (left and right)
        Alias,      // alias one SSAStatement (left)

        // Data:
        This,       // -
        Parameter,  // special:Integer=position
        Arg,        // left=value, special:Integer=position
        Null,       // special:Type=type of null, to distinguish null and 0

        Int,        // special:Integer=value
        Boolean,    // special:Boolean=value

        NewObj,     // special:String=type
        NewIntArray,// left=size

        // Control flow:
        Label,      // special:String=label
        Goto,       // special:String=label
        Branch,     // left=condition, special:String=label, jumps if the condition is true
        NBranch,    // left=condition, special:String=label, like branch, but jumps if condition is false

        // Calls:
        Call,       // left=target, special:SSACall=method and arguments
        Print,      // left=value

        Return,     // left=value

        // Member access:
        Member,     // left=target, special:String=member name
        Index,      // left=target, right=index

        // Stack:
        Store,      // left=value, special:Integer=stack offset
        Load,       // special:Integer=stack offset

        // Assignments:
        VarAssg,    // left=value, special=name (String), only used for type enforcement
        MemberAssg, // left=object, right=value, special=name (String)
        IndexAssg,  // left=array, right=value, special=index (SSAStatement)

        // Unary operator (left = operand):
        Not,

        // Binary operators (left, right = operands):
        Lt, Le, Eq, Ne, Gt, Ge,
        And, Or,
        Plus, Minus,
        Mul, Div, Mod,
    }

    // since the indices are just for debugging, we don't care about thread safety here
    static int NextIndex = 0;
    int index;

    // for debugging
    ASTNode ast;

    // the target register
    int register;

    // is this register pinned (i.e., is the register allocator not allowed to assign it?)
    boolean registerPinned;

    // the operation performed
    Op op;

    // the type of the result (default null until assigned)
    StaticType type;

    // the LHS and RHS for most operations are the results of former SSAStatements
    SSAStatement left, right;

    // but for some, there's a special value of some kind
    Object special;

    public SSAStatement(ASTNode ast, Op op, SSAStatement left, SSAStatement right, Object special) {
        this.index = NextIndex++;
        this.ast = ast;
        this.register = -1;
        this.op = op;
        this.left = left;
        this.right = right;
        this.special = special;
    }

    public SSAStatement(ASTNode ast, Op op) {
        this(ast, op, null, null, null);
    }

    public SSAStatement(ASTNode ast, Op op, SSAStatement left, SSAStatement right) {
        this(ast, op, left, right, null);
    }

    public SSAStatement(ASTNode ast, Op op, Object special) {
        this(ast, op, null, null, special);
    }

    public int getRegister() { return register; }
    public void setRegister(int to) { register = to; }
    public void pinRegister(int to) {
        register = to;
        registerPinned = true;
    }
    public boolean registerPinned() { return registerPinned; }
    public int getIndex() { return index; }
    public ASTNode getASTNode() { return ast; }
    public Op getOp() { return op; }
    public void setType(StaticType type) { this.type = type; }
    public StaticType getType() { return type; }
    public SSAStatement getLeft() { return left; }
    public void setLeft(SSAStatement to) { left = to; }
    public SSAStatement getRight() { return right; }
    public void setRight(SSAStatement to) { right = to; }
    public Object getSpecial() { return special; }
    public void setSpecial(Object to) { special = to; }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(this.getIndex());

        if (this.register >= 0) {
            sb.append("(");
            sb.append(this.register);
            sb.append(")");
        }

        sb.append(": ");
        sb.append(op);

        if (left != null) {
            sb.append(" ");
            sb.append(left.getIndex());
        }

        if (right != null) {
            sb.append(" ");
            sb.append(right.getIndex());
        }

        if (special != null) {
            sb.append(" *");
            sb.append(special);
        }

        if (type != null) {
            sb.append(" :");
            sb.append(type);
        }

        return sb.toString();
    }
}
