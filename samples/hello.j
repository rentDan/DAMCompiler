.class public hello
.super java/lang/Object
.method public <init>()V
    aload_0
    invokenonvirtual java/lang/Object/<init>()V
    return
.end method
.method public static main([Ljava/lang/String;)V
    .limit stack 32
    .limit locals 9

	ldc ""
	astore 1
	ldc ""
	astore 2
	ldc "Hello"
	astore 1
	ldc 3.0
	fstore 1
	ldc 0.0
	fstore 3
	START0:
	fload 3
	ldc 5.0
	fcmpl
	ifge END0
	getstatic java/lang/System/out Ljava/io/PrintStream;

	fload 3
	invokevirtual java/io/PrintStream/println(F)V
	fload 3
	ldc 1.0
	fadd
	fstore 3
	goto START0
	END0:
	ldc 3.0
	invokestatic java/lang/String.valueOf(F)Ljava/lang/String;
	astore 4
	aload 4
	invokestatic java/lang/Float/parseFloat(Ljava/lang/String;)F
	fstore 5
	fload 5
	fconst_0
	fcmpl
	istore 6
	ldc 1.0
	ldc 2.0
	fadd
	fconst_0
	fcmpl
	istore 7
	getstatic java/lang/System/out Ljava/io/PrintStream;

	aload 4
	invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
	getstatic java/lang/System/out Ljava/io/PrintStream;

	fload 5
	invokevirtual java/io/PrintStream/println(F)V
	getstatic java/lang/System/out Ljava/io/PrintStream;

	iload 6
	invokevirtual java/io/PrintStream/println(Z)V
	getstatic java/lang/System/out Ljava/io/PrintStream;

	iload 7
	invokevirtual java/io/PrintStream/println(Z)V
	ldc 2.0
	fconst_0
	fcmpl
	ifeq END1
	THEN1:
	getstatic java/lang/System/out Ljava/io/PrintStream;

	ldc "I should see this."
	invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
	END1:
    return
.end method
