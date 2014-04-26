package edu.purdue.cs352.minijava;

import edu.purdue.cs352.minijava.backend.*;
import edu.purdue.cs352.minijava.parser.*;
import edu.purdue.cs352.minijava.ast.*;
import edu.purdue.cs352.minijava.ssa.*;

// A simple frontend for the SSACompiler
public class MIPSCompilerFrontend {
    public static void usage() {
        System.out.println("Use: mjcompiler-mips <input filename>");
    }

    public static void main(String[] args) {
        String fname = null;
        ParserAST parser;
        Program prog;
        MIPSCompilerFrontend fe;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                usage();
                return;

            } else if (fname == null) {
                fname = arg;

            } else {
                usage();
                return;

            }
        }
        if (fname == null) {
            usage();
            return;
        }

        try {
            parser = new ParserAST(new java.io.FileInputStream(fname));
        } catch (java.io.FileNotFoundException ex) {
            System.out.println("File " + fname + " not found.");
            return;
        }

        try {
            prog = parser.Program();
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            return;
        }

        SSAProgram sprog = SSACompiler.compile(prog);

        TypeChecker tc = new TypeChecker(sprog);
        tc.typeCheck();

        System.out.println(AsmMIPS.compile(sprog));
    }
}
