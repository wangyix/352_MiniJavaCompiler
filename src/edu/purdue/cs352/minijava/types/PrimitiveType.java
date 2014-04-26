package edu.purdue.cs352.minijava.types;

public class PrimitiveType extends StaticType {
    @Override public boolean isPrimitive() { return true; }

    public static class IntType extends PrimitiveType {
        @Override public boolean subtypeOf(StaticType y) {
            if (y instanceof IntType) return true;
            return false;
        }

        @Override public String toString() { return "int"; }
    }

    public static class BooleanType extends PrimitiveType {
        @Override public boolean subtypeOf(StaticType y) {
            if (y instanceof BooleanType) return true;
            return false;
        }

        @Override public String toString() { return "boolean"; }
    }
}
