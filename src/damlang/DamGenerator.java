package damlang;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import damlang.Expr.Assign;
import damlang.Expr.Binary;
import damlang.Expr.Grouping;
import damlang.Expr.Literal;
import damlang.Expr.Logical;
import damlang.Expr.Typecast;
import damlang.Expr.Unary;
import damlang.Expr.Variable;
import damlang.Stmt.Block;
import damlang.Stmt.Expression;
import damlang.Stmt.If;
import damlang.Stmt.Let;
import damlang.Stmt.Print;
import damlang.Stmt.Read;
import damlang.Stmt.While;
//import damlang.Stmt.Typecast;

public class DamGenerator implements Expr.Visitor<String>, Stmt.Visitor<String> {
	private DamEnvironment env = new DamEnvironment();
	private List<Stmt> statements;
	private List<String> ins = new ArrayList<>();
	//ins == instructions

	private Map<Expr, String> t = new HashMap<>();
	private Map<String, String> javat = new HashMap<>();
	private Map<Object, String> conditionLabels = new HashMap<>();
	private int labelCounter = 0;


	private PrintWriter writer;
	
	private String jasminFilePath;

	public DamGenerator(List<Stmt> statements) {
		this.statements = statements;

		javat.put("double", "F");
		javat.put("str", "Ljava/lang/String;");
		javat.put("bool", "Z");

		env.define("args", "str"); //no arrays yet
	}
	/**
	 * Creates textual bytecode file and then uses Jasmin to
	 * generate the actual Java classfile.  It also removes the
	 * Jasmin file before exiting.
	 * @param absoluteStem
	 */
	public void generate(String absoluteStem) {
		// Visit the statements (and expressions) to generate the instructions in 'ins'.
		for (Stmt s : statements) {
			s.accept(this);
		}

		// Write the file, including 'ins'.
		writeClassfile(absoluteStem);
	}
	
	private void writeClassfile(String absoluteStem) {		
		jasminFilePath = absoluteStem + ".j";
		
		String javaClassName = absoluteStem;
		int slash = absoluteStem.lastIndexOf(File.separator);
		if (slash >= 0) {
			javaClassName = absoluteStem.substring(slash + 1);
		}
		
		try {
			writer = new PrintWriter(jasminFilePath);
			writeHeader(javaClassName);
			writeCtor();
			writeMainStart();
			for (String inst : ins) {
				writer.println("\t" + inst);
			}
			writeMainEnd();
		} catch (IOException ioe) {
			DamCompiler.error("Error generating bytecode. " + ioe.getMessage());
		} finally {
			writer.close();
		}
		
		// Run jasmin on our .j file to create the .class file.
		jasmin.Main jasminMain = new jasmin.Main();
		jasminMain.run(new String[]{jasminFilePath});
		
		// Remove the .j and then move the .class to the same location as the .dam file.
		//new File(jasminFilePath).delete();
		Path sourceClass = Paths.get(javaClassName + ".class");
		Path targetClass = Paths.get(absoluteStem + ".class");
		try {
			Files.move(sourceClass, targetClass, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
            DamCompiler.error("Fatal error: " + e.getMessage());
        } 
	}
	
	private void writeHeader(String javaClassName) {
		writer.println(".class public " + javaClassName + "\n"
				+ ".super java/lang/Object");
	}
	
	private void writeCtor() {
		writer.println(".method public <init>()V\n"
				+ "    aload_0\n"
				+ "    invokenonvirtual java/lang/Object/<init>()V\n"
				+ "    return\n"
				+ ".end method");
	}

	private void writeMainStart() {
		writer.println(".method public static main([Ljava/lang/String;)V\n"
				+ "    .limit stack " + ((ins.size()/2)+2) + "\n"
				+ "    .limit locals " + (env.numVars()+1) + "\n");
	}

	private void writeMainEnd() {
		writer.println("    return\n"
				+ ".end method");
	}

	// Used by Binary when expression isn't a conditional with a jumpLabel
	// It will leave a bool on the stack instead of a conditional jump
	private void boolInsAdder(TokenType type, String mode) {
		String helper = "";
		if (mode == "bool") {
			if (type == TokenType.BANG_EQUAL) 		helper = "if_icmpeq";
			if (type == TokenType.EQUAL_EQUAL) 		helper = "if_icmpne";
		} else {
			if(type == TokenType.BANG_EQUAL) 		helper = "ifeq";
			if(type == TokenType.EQUAL_EQUAL) 		helper = "ifne";
			if(type == TokenType.GREATER) 			helper = "ifle";
			if(type == TokenType.GREATER_EQUAL) 	helper = "iflt";
			if(type == TokenType.LESS) 				helper = "ifge";
			if(type == TokenType.LESS_EQUAL) 		helper = "ifgt";
		}

		ins.add(helper + " FALSE");
		ins.add("ldc 1");
		ins.add("goto END" + labelCounter);
		ins.add("FALSE:");
		ins.add("ldc 0");
		ins.add("END" + labelCounter + ":");
		labelCounter++;
	}

	// Used by If and While statements when the condition is a truthy statement
	// It will add different instructions than a binary condition
	private void truthyInsAdder(String type, String label) {
		if (type.equals("double")) {
			ins.add("fconst_0");
			ins.add("fcmpl");
			ins.add("ifeq " + label);
		} else if (type.equals("str")) {
			ins.add("ldc \"\"");
			ins.add("invokevirtual java/lang/String/compareTo(Ljava/lang/String;)I");
			ins.add("ifeq " + label);
		} else if (type.equals("bool")) {
			ins.add("ldc 0");
			ins.add("if_icmpeq " + label);
		}
	}

	@Override
	public String visitBlockStmt(Block stmt) {
		for (Stmt st: stmt.statements) {
			st.accept(this);
		}

		return null;
	}

	@Override
	public String visitExpressionStmt(Expression stmt) {
		stmt.expression.accept(this);
		return null;
	}

	@Override
	public String visitIfStmt(If stmt) {
		String thenLabel = "THEN" + labelCounter;
		String elseLabel = "ELSE" + labelCounter;
		String endLabel = "END" + labelCounter++;

		if (stmt.elseBranch == null) {
			conditionLabels.put(stmt.condition, endLabel);
		} else {
			conditionLabels.put(stmt.condition, elseLabel);
		}

		stmt.condition.accept(this);
		//if the condition was truthy, jump label wouldn't be made,
		//make it
		if (!stmt.condition.getClass().equals(Binary.class)) {
			String jumpLabel = conditionLabels.get(stmt.condition);
			truthyInsAdder(t.get(stmt.condition), jumpLabel);
		}

		ins.add(thenLabel + ":");
		stmt.thenBranch.accept(this);

		if (stmt.elseBranch != null) {
			ins.add("goto " + endLabel);
			ins.add(elseLabel + ":");
			stmt.elseBranch.accept(this);
		}

		ins.add(endLabel + ":");

		return null;
	}

	@Override
	public String visitPrintStmt(Print stmt) {
		ins.add("getstatic java/lang/System/out Ljava/io/PrintStream;\n");
		stmt.expression.accept(this);

		String exprType = t.get(stmt.expression);
		String javaType = javat.get(exprType);

		ins.add("invokevirtual java/io/PrintStream/println("
				+ javaType + ")V");

		return null;
	}

	public String visitReadStmt(Read stmt) {
		//make sure the variable exists - get method returns error if not
		env.get(stmt.name);

		//make the variable a string
		env.assign(stmt.name, "str");

		//find index of variable
		int varIndex = env.getIndex(stmt.name);

		//add instructions
		ins.add("new java/util/Scanner");
		ins.add("dup");
		ins.add("getstatic java/lang/System/in Ljava/io/InputStream;");
		ins.add("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V");
		ins.add("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;");
		ins.add("astore " + varIndex);

		return null;
	}

	@Override
	public String visitLetStmt(Let stmt) {
		//if there is no initializer, assign the variable an empty string
		if (stmt.initializer == null) {
			env.define(stmt.name.lexeme, "str");
			ins.add("ldc \"\"");
			int varIndex = env.getIndex(stmt.name);
			ins.add("astore " + varIndex);
			return null;
		}

		stmt.initializer.accept(this);

		String rhsType = t.get(stmt.initializer);
		env.define(stmt.name.lexeme, rhsType);
		int varIndex = env.getIndex(stmt.name);

		if (rhsType.equals("double")) {
			ins.add("fstore " + varIndex);
		} else if (rhsType.equals("str")) {
			ins.add("astore " + varIndex);
		} else if (rhsType.equals("bool")) {
			ins.add("istore " + varIndex);
		}
		return null;
	}

	@Override
	public String visitWhileStmt(While stmt) {
		//create start and end label
		String startLabel = "START" + labelCounter;
		String endLabel = "END" + labelCounter++;

		//add start label ins.
		ins.add(startLabel + ":");

		//add condition-label to map
		conditionLabels.put(stmt.condition, endLabel);

		//add condition and body ins.
		stmt.condition.accept(this);

		//if it is a truthy statement, we need to create the condition ourselves
		if (!stmt.condition.getClass().equals(Binary.class)) {
			String jumpLabel = conditionLabels.get(stmt.condition);
			truthyInsAdder(t.get(stmt.condition), jumpLabel);
		}

		stmt.body.accept(this);

		//add goto and endlabel ins.
		ins.add("goto " + startLabel);
		ins.add(endLabel + ":");
		return null;
	}

	@Override
	public String visitBinaryExpr(Binary expr) {
		expr.left.accept(this);
		expr.right.accept(this);
		String lType = t.get(expr.left);
		String rType = t.get(expr.right);
		if (! lType.equals(rType)) {
			DamCompiler.error("Type mismatch on line " + expr.operator.line
					+ ". Cannot apply " + expr.operator.lexeme + " to '"
					+ lType + "' and '" + rType + "'.");
		}

		switch (expr.operator.type) {
			case TokenType.PLUS:
			case TokenType.MINUS:
			case TokenType.STAR:
			case TokenType.SLASH:
				if (lType.equals("double")) {
					if (expr.operator.type == TokenType.PLUS) 		ins.add("fadd");
					else if (expr.operator.type == TokenType.MINUS) ins.add("fsub");
					else if (expr.operator.type == TokenType.STAR) 	ins.add("fmul");
					else if (expr.operator.type == TokenType.SLASH) ins.add("fdiv");
				} else if (lType.equals("str")) {
					if (expr.operator.type == TokenType.PLUS) {
						ins.add("invokevirtual java/lang/String/concat(" +
								"Ljava/lang/String;)Ljava/lang/String;");
					} else {
						DamCompiler.error("Cannot apply operator " + expr.operator.lexeme + " to strings.");
					}
				}
				t.put(expr, lType);
				break;

			case TokenType.BANG_EQUAL:
			case TokenType.EQUAL_EQUAL:
			case TokenType.GREATER:
			case TokenType.GREATER_EQUAL:
			case TokenType.LESS:
			case TokenType.LESS_EQUAL:
				t.put(expr, "bool");

				if(lType.equals("double")) {
					ins.add("fcmpl");
				} else if (lType.equals("str")) {
					ins.add(("invokevirtual java/lang/String/compareTo(Ljava/lang/String;)I"));
				} else if (lType.equals("bool")) {
					String jumpLabel = conditionLabels.get(expr);

					//if no jumplabel, then put 0 or 1 on stack
					if (jumpLabel == null) {
						boolInsAdder(expr.operator.type, lType);
						break;
					}

					if(expr.operator.type == TokenType.BANG_EQUAL) 	ins.add("if_icmpeq " + jumpLabel);
					if(expr.operator.type == TokenType.EQUAL_EQUAL) ins.add("if_icmpne " + jumpLabel);
					break;
				}

				String jumpLabel = conditionLabels.get(expr);

				//if we are doing a comparison for something other than an if/while condition,
				//we need to change the way we handle them.
				//Depending on the operator, we need to add a 1 or 0 on stack
				//this will let whatever visited this use the bool
				if (jumpLabel == null) {
					boolInsAdder(expr.operator.type, lType);
					break;
				}

				if(expr.operator.type == TokenType.BANG_EQUAL) 		ins.add("ifeq " + jumpLabel);
				if(expr.operator.type == TokenType.EQUAL_EQUAL) 	ins.add("ifne " + jumpLabel);
				if(expr.operator.type == TokenType.GREATER) 		ins.add("ifle " + jumpLabel);
				if(expr.operator.type == TokenType.GREATER_EQUAL) 	ins.add("iflt " + jumpLabel);
				if(expr.operator.type == TokenType.LESS) 			ins.add("ifge " + jumpLabel);
				if(expr.operator.type == TokenType.LESS_EQUAL) 		ins.add("ifgt " + jumpLabel);

				break;

			default:
		}
		return null;
	}

	@Override
	public String visitGroupingExpr(Grouping expr) {
		expr.expression.accept(this);
		t.put(expr, t.get(expr.expression));
		return null;
	}

	@Override
	public String visitLiteralExpr(Literal expr) {

		if (expr.value instanceof Double) {
			t.put(expr, "double");
			ins.add("ldc " + expr.value);
		} else if (expr.value instanceof String) {
			t.put(expr, "str");
			ins.add("ldc \"" + expr.value + "\"");
		} else if (expr.value instanceof Boolean) {
			t.put(expr, "bool");
			if (expr.value.equals(false)) {
				ins.add("iconst_0");
			} else {
				ins.add("iconst_1");
			}
		}
		return null;
	}

	@Override
	public String visitLogicalExpr(Logical expr) {
		/* not required to be implemented */

		//and/or

		//expr.left.accept(this);
		//expr.right.accept(this);

		/*
		if (expr.operator.type == TokenType.AND) {
			//with and, if the left side is false, go to else
			//if the left side is true, keep going to right side
			//if the right side is false, go to else
			//if the right side is true, go to then



		} else if (expr.operator.type == TokenType.OR) {
			//with or, if the left side is true, go to then
			//if the left side is false, keep going to right side
			//if the right side is true, go to then
			//if the right side is false, go to else
		} */

		return null;
	}

	@Override
	public String visitVariableExpr(Variable expr) {
		String type = env.get(expr.name);
		t.put(expr, type);

		if (type.equals("double")) {
			ins.add("fload " + env.getIndex(expr.name));
		} else if (type.equals("str")) {
			ins.add("aload " + env.getIndex(expr.name));
		} else if (type.equals("bool")) {
			ins.add("iload " + env.getIndex(expr.name));
		}
		return null;
	}

	public String visitTypecastExpr(Typecast expr) {
		expr.value.accept(this);
		String rhsType = t.get(expr.value);

		//get type of the Token type
		String type = expr.type.lexeme;

		t.put(expr, type);

		//based on what expr.type is, convert type of rhsType to expr.type
		if (type.equals("double")) {
			if (rhsType.equals("str")) {
				ins.add("invokestatic java/lang/Float/parseFloat(Ljava/lang/String;)F");
			} else if (rhsType.equals("bool")) {
				ins.add("i2f");
			}
		} else if (type.equals("str")) {
			if (rhsType.equals("double")) {
				ins.add("invokestatic java/lang/String.valueOf(F)Ljava/lang/String;");
			} else if (rhsType.equals("bool")) {
				ins.add("invokestatic java/lang/String.valueOf(Z)Ljava/lang/String;");
			}
		} else if (type.equals("bool")) {
			//truthy statements
			//if rhsType is double, compare to 0.0
			if (rhsType.equals("double")) {
				ins.add("fconst_0");
				ins.add("fcmpl");
			//if rhsType is str, compare to ""
			} else if (rhsType.equals("str")) {
				ins.add("ldc \"\"");
				ins.add("invokevirtual java/lang/String/compareTo(Ljava/lang/String;)I");
			}
		}
		return null;
	}

	@Override
	public String visitUnaryExpr(Unary expr) {
		expr.right.accept(this);
		String rType = t.get(expr.right);
		t.put(expr, rType);

		//THIS WORKS
		if (expr.operator.type == TokenType.MINUS) {
			if (rType.equals("double")) {
				ins.add("ldc -1.0");
				ins.add("fmul");
			} else {
				DamCompiler.error("Cannot apply operator " + expr.operator.lexeme + " to strings or booleans.");
			}

		} else if (expr.operator.type == TokenType.BANG) {

			if (rType.equals("bool")) {
				//do bytecode instructions to flip whatever is on the stack
				//push 1 to stack and xor
				ins.add("iconst_1");
				ins.add("ixor");

			} else {
				DamCompiler.error("Cannot apply operator " + expr.operator.lexeme + " to strings or doubles.");
			}
		}

		return null;
	}

	@Override
	public String visitAssignExpr(Assign expr) {

		//check if variable exists - if not get method will throw error
		env.get(expr.name);

		//accept expression
		expr.right.accept(this);
		//get type of expression
		String rhsType = t.get(expr.right);

		//get index of variable
		int varIndex = env.getIndex(expr.name);

		//assign type of variable
		env.assign(expr.name, rhsType);

		//assign value to variable depending on type
		if (rhsType.equals("double")) {
			ins.add("fstore " + varIndex);
		} else if (rhsType.equals("str")) {
			ins.add("astore " + varIndex);
		} else if (rhsType.equals("bool")) {
			ins.add("istore " + varIndex);
		}
		return null;
	}

}
