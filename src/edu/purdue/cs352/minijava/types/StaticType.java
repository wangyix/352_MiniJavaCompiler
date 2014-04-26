package edu.purdue.cs352.minijava.types;

// this class only exists to be a parent of PrimitiveType and ObjectType
public class StaticType {
    public boolean isPrimitive() { return false; }
    public boolean isObject() { return false; }

    public boolean subtypeOf(StaticType y) {
        return false;
    }

    public StaticType commonSupertype(StaticType y) {
        if (this == y) return this;
        return null;
    }
    
    public boolean equals(StaticType type) {
    	return this==type;
    }
}
