package exm.stc.tests;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.ic.opt.valuenumber.ValueNumber;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LocalFunctionCall;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.TurbineOp;

public class ValueNumberRegression {


  @BeforeClass
  public static void setupLogging() {
    Logging.setupLogging("ValueNumberRegression.stc.log", true);
  }

  @Test
  public void testValueNumber() throws UserException {
    ForeignFunctions ff = new ForeignFunctions();
    Program prog = new Program(ff);
    Function entry = new Function(Constants.ENTRY_FUNCTION, Var.NONE, Var.NONE,
        ExecTarget.syncControl());
    prog.addFunction(entry);

    Block main = entry.mainBlock();

    Var x = main.declare(Types.F_INT, "x", Alloc.STACK, DefType.LOCAL_USER,
        VarProvenance.unknown(), false);
    Var cond = main.declare(Types.V_BOOL, "cond", Alloc.STACK,
        DefType.LOCAL_USER, VarProvenance.unknown(), false);

    main.addStatement(new LocalFunctionCall("fake" ,"fake", Arg.NONE,
                              cond.asList(), prog.foreignFunctions()));

    // If statement condition that can't be resolved to constant
    IfStatement ifStmt = new IfStatement(cond.asArg());
    WaitStatement waitStmt = new WaitStatement("",
        new WaitVar(x, false).asList(), PassedVar.NONE, Var.NONE,
        WaitMode.WAIT_ONLY, false, ExecTarget.nonDispatchedAny(),
        new TaskProps());

    main.addStatement(ifStmt);
    ifStmt.thenBlock().addContinuation(waitStmt);

    Var vx = waitStmt.getBlock().declare(Types.V_INT, "v:x", Alloc.STACK,
        DefType.LOCAL_USER, VarProvenance.unknown(), false);
    Instruction retrieveX = TurbineOp.retrieveScalar(vx, x);
    waitStmt.getBlock().addStatement(retrieveX);

    main.addStatement(TurbineOp.storePrim(x, Arg.newInt(42)));

    System.err.println(entry.toString());
    System.err.println();

    ValueNumber optPass = new ValueNumber(true);
    optPass.optimize(Logging.getSTCLogger(), prog);

    System.err.println(entry.toString());

    assertTrue("Wait vars removed", waitStmt.getWaitVars().isEmpty());
    assertTrue("Arg was replaced with constant in: " + retrieveX,
        retrieveX.getInput(0).getVar().defType() == DefType.GLOBAL_CONST);
  }

}
