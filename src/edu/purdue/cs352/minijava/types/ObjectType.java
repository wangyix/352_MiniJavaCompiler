package edu.purdue.cs352.minijava.types;

import java.util.*;

public class ObjectType extends StaticType {
    String name;
    ObjectType superType;

    public ObjectType(String name, ObjectType superType) {
        this.name = name;
        this.superType = superType;
    }

    @Override public boolean subtypeOf(StaticType y) {
        ObjectType x = this;

        while (x != null) {
            if (x == y) return true;
            x = x.getSuperType();
        }

        return false;
    }

    @Override public StaticType commonSupertype(StaticType y) {
        if (!(y instanceof ObjectType)) return null;

        ObjectType xo = this;
        ObjectType yo = (ObjectType) y;

        // make a set of the full list of types for the right
        Set<ObjectType> types = new HashSet<ObjectType>();
        while (yo != null) {
            types.add(yo);
            yo = yo.getSuperType();
        }

        // then find the first supertype in the list
        while (xo != null) {
            if (types.contains(xo)) return xo;
            xo = xo.getSuperType();
        }

        // no match found!
        return null;
    }

    @Override public boolean isObject() { return true; }

    public String getName() { return name; }
    public ObjectType getSuperType() { return superType; }

    @Override public String toString() { return name; }
}
