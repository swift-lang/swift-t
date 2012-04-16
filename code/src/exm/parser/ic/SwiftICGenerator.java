package exm.parser.ic;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.apache.log4j.Logger;

import exm.ast.Types;
import exm.ast.Variable;
import exm.ast.Types.FunctionType;
import exm.ast.Types.SwiftType;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.CompilerBackend;
import exm.parser.ic.ICContinuations.ForeachLoop;
import exm.parser.ic.ICContinuations.IfStatement;
import exm.parser.ic.ICContinuations.Loop;
import exm.parser.ic.ICContinuations.NestedBlock;
import exm.parser.ic.ICContinuations.RangeLoop;
import exm.parser.ic.ICContinuations.SwitchStatement;
import exm.parser.ic.ICContinuations.WaitStatement;
import exm.ast.Builtins.ArithOpcode;
import exm.ast.Builtins.UpdateMode;
import exm.parser.ic.ICInstructions.Comment;
import exm.parser.ic.ICInstructions.FunctionCallInstruction;
import exm.parser.ic.ICInstructions.LocalArithOp;
import exm.parser.ic.ICInstructions.LoopBreak;
import exm.parser.ic.ICInstructions.LoopContinue;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.ICInstructions.TurbineOp;
import exm.parser.ic.SwiftIC.AppFunction;
import exm.parser.ic.SwiftIC.Block;
import exm.parser.ic.SwiftIC.BlockType;
import exm.parser.ic.SwiftIC.BuiltinFunction;
import exm.parser.ic.SwiftIC.CompFunction;
import exm.parser.ic.SwiftIC.Program;
import exm.parser.util.InvalidWriteException;
import exm.parser.util.ParserRuntimeException;
import exm.parser.util.UndefinedTypeException;
import exm.parser.util.UserException;

public class SwiftICGenerator implements CompilerBackend {

  private final Logger logger;
  private Program program;

  // Keep track of current place in program
  private CompFunction currComposite = null;
  private final Deque<Block> blockStack = new ArrayDeque<Block>();
  
  private final Deque<Loop> loopStack = new ArrayDeque<Loop>();
  
//Place to log IC to (can be null for no output)
  private PrintStream icOutput;

  private Block currBlock() {
    return blockStack.peek();
  }

  public SwiftICGenerator(Logger logger, PrintStream icOutput) {
    this.logger = logger;
    this.program = new Program();
    this.icOutput = icOutput;
  }



  @Override
  public String code() {
    return program.toString();
  }

  @Override
  public void optimise() throws InvalidWriteException {
    logger.debug("Optimising Swift IC");
    this.program = SwiftICOptimiser.optimise(logger, icOutput, program);
    logger.debug("Optimisation done");
  }


  /**
   * Recreate an equivalent series of calls that were used
   * to create the program
   * @throws UserException
   */
  @Override
  public void regenerate(CompilerBackend backend) throws UserException {
    logger.debug("Using Swift IC to regenerate code");
    this.program.generate(logger, backend);
    logger.debug("Done using Swift IC to regenerate code");
  }


  public Program getProgram() {
    return program;
  }

  @Override
  public void header() {
    // do nothing
  }

  @Override
  public void turbineStartup() {
    // do nothing
  }

  @Override
  public void defineBuiltinFunction(String name, String pkg,
                                    String version, String symbol,
                                    FunctionType fType)
  throws UserException
  {
    assert(blockStack.size() == 0);
    assert(currComposite == null);
    BuiltinFunction bf =
        new BuiltinFunction(name, pkg, version, symbol, fType);
    program.addBuiltin(bf);
  }

  @Override
  public void startCompositeFunction(String functionName, List<Variable> oList,
      List<Variable> iList, boolean async) throws UserException {
    assert(blockStack.size() == 0);
    assert(currComposite == null);
    currComposite = new CompFunction(functionName, iList, oList, async);
    program.addComposite(currComposite);
    blockStack.add(currComposite.getMainblock());
  }

  @Override
  public void endCompositeFunction() {
    assert(currComposite != null);
    assert(blockStack.size() == 1);

    currComposite = null;
    blockStack.pop();
  }

  @Override
  public void defineApp(String functionName, List<Variable> iList,
      List<Variable> oList, String body) {
    AppFunction fn = new AppFunction(functionName, iList, oList, body);
    program.addAppFun(fn);
  }

  @Override
  public void startNestedBlock() {
    NestedBlock b = new NestedBlock();
    currBlock().addContinuation(b);
    blockStack.push(b.getBlock());
  }

  @Override
  public void endNestedBlock() {
    assert(blockStack.size() >= 2);
    assert(currBlock().getType() == BlockType.NESTED_BLOCK);
    blockStack.pop();
  }

  @Override
  public void addComment(String comment) {
    currBlock().addInstruction(new Comment(comment));
  }

  @Override
  public void startIfStatement(Variable condition, boolean hasElse) {
    assert(currComposite != null);
    assert(condition.getType().equals(Types.VALUE_INTEGER)
          || condition.getType().equals(Types.VALUE_BOOLEAN));

    IfStatement stmt = new IfStatement(condition);
    currBlock().addContinuation(stmt);

    if (hasElse) {
      blockStack.push(stmt.getElseBlock());
    }

    blockStack.push(stmt.getThenBlock());
  }

  @Override
  public void startElseBlock() {
    // Should still be then, else, finally and top level procedure
    assert(blockStack.size() >= 4);
    assert(currBlock().getType() == BlockType.THEN_BLOCK);
    blockStack.pop();
  }

  @Override
  public void endIfStatement() {
    // Should still be finally and enclosing block
    assert(blockStack.size() >= 2);
    assert(currBlock().getType() == BlockType.ELSE_BLOCK ||
        currBlock().getType() == BlockType.THEN_BLOCK);
    blockStack.pop();
  }


  @Override
  public void startWaitStatement(String procName, List<Variable> waitVars,
      List<Variable> usedVariables, List<Variable> containersToRegister,
      boolean explicit) {
    assert(currComposite != null);
    WaitStatement wait = new WaitStatement(procName, waitVars, usedVariables,
                                              containersToRegister, explicit);
    currBlock().addContinuation(wait);
    blockStack.push(wait.getBlock());
  }

  @Override
  public void endWaitStatement(List<Variable> containersToRegister) {
    assert(currBlock().getType() == BlockType.WAIT_BLOCK);
    blockStack.pop();
  }

  @Override
  public void startSwitch(Variable switchVar,
      List<Integer> caseLabels, boolean hasDefault) {

    logger.trace("startSwitch() stack size:" + blockStack.size());
    SwitchStatement sw = new SwitchStatement(switchVar, caseLabels);
    currBlock().addContinuation(sw);
    // Add blocks to stack in reverse order
    if (hasDefault) {
      blockStack.push(sw.getDefaultBlock());
    }

    // Reverse case list
    ArrayList<Block> casesReversed = new ArrayList<Block>(sw.caseBlocks());
    Collections.reverse(casesReversed);
    for (Block caseBlock: casesReversed) {
      blockStack.push(caseBlock);
    }
  }

  @Override
  public void endCase() {
    logger.trace("endCase() stack size:" + blockStack.size());
    // case, finally, enclosing at minimum
    assert(blockStack.size() >= 2);
    assert(currBlock().getType() == BlockType.CASE_BLOCK);
    blockStack.pop();
  }

  @Override
  public void endSwitch() {
    logger.trace("endSwitch() stack size:" + blockStack.size());
    // all cases should already be off stack, do nothing
  }

  @Override
  public void startForeachLoop(Variable arrayVar, Variable memberVar,
                  Variable loopCountVar, boolean isSync, 
                  int splitDegree, boolean arrayClosed,
         List<Variable> usedVariables, List<Variable> containersToRegister) {
    if(!Types.isArray(arrayVar.getType())) {
      throw new ParserRuntimeException("foreach loop over non-array: " + 
                arrayVar.toString()); 
    }
    assert(arrayVar.getType().getMemberType().equals(memberVar.getType()));
    assert(loopCountVar == null || 
              loopCountVar.getType().equals(Types.VALUE_INTEGER));
    ForeachLoop loop = new ForeachLoop(arrayVar, memberVar, 
                loopCountVar, isSync, splitDegree, arrayClosed, usedVariables, 
                containersToRegister);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endForeachLoop(boolean isSync, int splitDegree, 
            boolean arrayClosed, List<Variable> containersToRegister) {
    assert(blockStack.peek().getType() == BlockType.FOREACH_BODY);
    blockStack.pop();
  }

  @Override
  public void startRangeLoop(String loopName, Variable loopVar,
      Oparg start, Oparg end, Oparg increment, boolean isSync,
      List<Variable> usedVariables, List<Variable> containersToRegister,
      int desiredUnroll, int splitDegree) {
    RangeLoop loop = new RangeLoop(loopName, loopVar, start, end, increment,
                                isSync, usedVariables, containersToRegister,
                                desiredUnroll, splitDegree);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endRangeLoop(boolean isSync, 
                          List<Variable> containersToRegister,
                          int splitDegree) {
    assert(currBlock().getType() == BlockType.RANGELOOP_BODY);
    blockStack.pop();
  }

  @Override
  public void startLoop(String loopName, List<Variable> loopVars,
      List<Variable> initVals, List<Variable> usedVariables,
      List<Variable> containersToRegister, List<Boolean> blockingVars) {
    Loop loop = new Loop(loopName, loopVars, initVals,
        usedVariables, containersToRegister, blockingVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
    loopStack.push(loop);
  }

  @Override
  public void loopContinue(List<Variable> newVals, 
        List<Variable> usedVariables, List<Variable> registeredContainers,
        List<Boolean> blockingVars) {
    LoopContinue inst = new LoopContinue(newVals, usedVariables,
        registeredContainers, blockingVars);
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopContinue(inst);
  }

  @Override
  public void loopBreak(List<Variable> containersToClose) {
    LoopBreak inst = new LoopBreak(
        new ArrayList<Variable>(containersToClose));
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopBreak(inst);
  }

  @Override
  public void endLoop() {
    Block b = blockStack.pop();
    loopStack.pop();
    assert(b.getType() == BlockType.LOOP_BODY);
  }
  
  @Override
  public void declare(SwiftType type, String name, VariableStorage storage,
      DefType defType, String mapping)
      throws UndefinedTypeException {
    assert(mapping == null || Types.isMappable(type));
    assert(mapping != null || (!Types.requiresMapping(type)));
    currBlock().declareVariable(type, name, storage, defType, mapping);
  }


  @Override
  public void builtinFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, Oparg priority) {
    assert(priority == null || priority.isImmediateInt());
    currBlock().addInstruction(
        FunctionCallInstruction.createBuiltinCall(
            function, inputs, outputs, priority));
  }

  @Override
  public void compositeFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, List<Boolean> blockOn, boolean async, 
      Oparg priority) {
    assert(priority == null || priority.isImmediateInt());
    if (blockOn != null) {
      throw new ParserRuntimeException("Swift IC generator doesn't support " +
      		" blocking on composite function inputs");
    }
    currBlock().addInstruction(
          FunctionCallInstruction.createCompositeCall(
              function, inputs, outputs, async, priority));
  }

  @Override
  public void appFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, Oparg priority) {
    assert(priority == null || priority.isImmediateInt());
    currBlock().addInstruction(
          FunctionCallInstruction.createAppCall(function, inputs, outputs, priority));
  }


  @Override
  public void closeArray(Variable arr) {
    assert(Types.isArray(arr.getType()));
    currBlock().addArrayToClose(arr);
  }

  @Override
  public void arrayLookupFuture(Variable oVar, Variable arrayVar,
      Variable indexVar, boolean isArrayRef) {
    assert(indexVar.getType().equals(Types.FUTURE_INTEGER));
    if (isArrayRef) {
      currBlock().addInstruction(
          TurbineOp.arrayRefLookupFuture(oVar, arrayVar, indexVar));
    } else {
      currBlock().addInstruction(
          TurbineOp.arrayLookupFuture(oVar, arrayVar, indexVar));
    }
  }

  @Override
  public void arrayLookupRefImm(Variable oVar, Variable arrayVar,
      Oparg arrIx, boolean isArrayRef) {
    assert(arrIx.isImmediateInt());
    if (isArrayRef) {
      currBlock().addInstruction(
          TurbineOp.arrayRefLookupImm(oVar, arrayVar, arrIx));
    } else {
      currBlock().addInstruction(
          TurbineOp.arrayLookupRefImm(oVar, arrayVar, arrIx));
    }
  }
  
  @Override
  public void arrayLookupImm(Variable oVar, Variable arrayVar,
      Oparg arrIx) {
    assert(oVar.getStorage() == VariableStorage.ALIAS);
    assert(Types.isArray(arrayVar.getType())); // Can't be reference to array
    assert(arrIx.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.arrayLookupImm(oVar, arrayVar, arrIx));
  }

  @Override
  public void arrayInsertFuture(Variable iVar, Variable arrayVar,
      Variable indexVar) {
    assert(indexVar.getType().equals(Types.FUTURE_INTEGER));
    currBlock().addInstruction(
        TurbineOp.arrayInsertFuture(iVar, arrayVar, indexVar));
  }

  @Override
  public void arrayRefInsertFuture(Variable iVar,
      Variable arrayVar, Variable indexVar, Variable outerArrayVar) {
    assert(indexVar.getType().equals(Types.FUTURE_INTEGER));
    assert(Types.isArrayRef(arrayVar.getType()));
    currBlock().addInstruction(
        TurbineOp.arrayRefInsertFuture(iVar, arrayVar, indexVar, 
                                              outerArrayVar));
  }
  
  @Override
  public void arrayInsertImm(Variable iVar, Variable arrayVar,
      Oparg arrIx) {
    assert(arrIx.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.arrayInsertImm(iVar, arrayVar, arrIx));
  }
  
  @Override
  public void arrayRefInsertImm(Variable iVar, Variable arrayVar,
          Oparg arrIx, Variable outerArrayVar) {
    assert(arrIx.isImmediateInt());
    assert(Types.isArrayRef(arrayVar.getType()));
    currBlock().addInstruction(
        TurbineOp.arrayRefInsertImm(iVar, arrayVar, arrIx, outerArrayVar));
  }

  @Override
  public void arrayCreateNestedFuture(Variable arrayResult,
      Variable arrayVar, Variable indexVar) {
    assert(Types.isArrayRef(arrayResult.getType()));
    assert(Types.isArray(arrayVar.getType()));
    assert(indexVar.getType().equals(Types.FUTURE_INTEGER));

    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedComputed(arrayResult,
          arrayVar, indexVar));
  }

  @Override
  public void arrayCreateNestedImm(Variable arrayResult,
      Variable arrayVar, Oparg arrIx) {
    assert(Types.isArray(arrayResult.getType()));
    assert(Types.isArray(arrayVar.getType()));
    assert(arrayResult.getStorage() == VariableStorage.ALIAS);
    assert(arrIx.isImmediateInt());

    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedImm(arrayResult,
          arrayVar, arrIx));
  }

  @Override
  public void arrayRefCreateNestedImm(Variable arrayResult,
      Variable arrayVar, Oparg arrIx) {
    assert(Types.isArrayRef(arrayResult.getType()));
    assert(Types.isArrayRef(arrayVar.getType()));
    assert(arrIx.isImmediateInt());

    currBlock().addInstruction(
      TurbineOp.arrayRefCreateNestedImmIx(arrayResult, arrayVar, arrIx));
  }

  @Override
  public void arrayRefCreateNestedFuture(Variable arrayResult,
      Variable arrayVar, Variable indexVar) {
    assert(Types.isArrayRef(arrayResult.getType()));
    assert(Types.isArrayRef(arrayVar.getType()));
    assert(indexVar.getType().equals(Types.FUTURE_INTEGER));

    currBlock().addInstruction(
      TurbineOp.arrayRefCreateNestedComputed(arrayResult,
          arrayVar, indexVar));

  }

  @Override
  public void assignReference(Variable target, Variable src) {
    currBlock().addInstruction(
        TurbineOp.addressOf(target, src));
  }


  @Override
  public void dereferenceInt(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_INTEGER));
    assert(src.getType().equals(Types.REFERENCE_INTEGER));
    currBlock().addInstruction(
        TurbineOp.dereferenceInt(target, src));
  }
  
  @Override
  public void dereferenceBool(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_BOOLEAN));
    assert(src.getType().equals(Types.REFERENCE_BOOLEAN));
    currBlock().addInstruction(
        TurbineOp.dereferenceBool(target, src));
  }

  @Override
  public void dereferenceFloat(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_FLOAT));
    assert(src.getType().equals(Types.REFERENCE_FLOAT));
    currBlock().addInstruction(
        TurbineOp.dereferenceFloat(target, src));
  }

  @Override
  public void dereferenceString(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_STRING));
    assert(src.getType().equals(Types.REFERENCE_STRING));
    currBlock().addInstruction(
        TurbineOp.dereferenceString(target, src));
  }

  @Override
  public void dereferenceBlob(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_BLOB));
    assert(src.getType().equals(Types.REFERENCE_BLOB));
    currBlock().addInstruction(
        TurbineOp.dereferenceBlob(target, src));
  }
  
  @Override
  public void retrieveRef(Variable target, Variable src) {
    assert(Types.isReference(src.getType()));
    assert(Types.isReferenceTo(src.getType(), target.getType()));
    currBlock().addInstruction(
        TurbineOp.retrieveRef(target, src));

  }
  
  @Override
  public void makeAlias(Variable dst, Variable src) {
    assert(src.getType().equals(dst.getType()));
    assert(dst.getStorage() == VariableStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.copyRef(dst, src));
  }

  @Override
  public void assignInt(Variable target, Oparg src) {
    assert(target.getType().equals(Types.FUTURE_INTEGER));
    assert(src.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.assignInt(target, src));
  }

  @Override
  public void retrieveInt(Variable target, Variable source) {
    assert(target.getType().equals(Types.VALUE_INTEGER));
    assert(source.getType().equals(Types.FUTURE_INTEGER));
    currBlock().addInstruction(
        TurbineOp.retrieveInt(target, source));
  }

  @Override
  public void assignBool(Variable target, Oparg src) {
    assert(target.getType().equals(Types.FUTURE_BOOLEAN));
    assert(src.isImmediateBool());
    currBlock().addInstruction(
        TurbineOp.assignBool(target, src));
  }

  @Override
  public void retrieveBool(Variable target, Variable source) {
    assert(target.getType().equals(Types.VALUE_BOOLEAN));
    assert(source.getType().equals(Types.FUTURE_BOOLEAN));
    currBlock().addInstruction(
        TurbineOp.retrieveBool(target, source));
  }

  @Override
  public void assignFloat(Variable target, Oparg src) {
    assert(target.getType().equals(Types.FUTURE_FLOAT));
    assert(src.isImmediateFloat());
    currBlock().addInstruction(
        TurbineOp.assignFloat(target, src));
  }

  @Override
  public void retrieveFloat(Variable target, Variable source) {
    assert(target.getType().equals(Types.VALUE_FLOAT));
    assert(source.getType().equals(Types.FUTURE_FLOAT));
    currBlock().addInstruction(
        TurbineOp.retrieveFloat(target, source));
  }

  @Override
  public void assignString(Variable target, Oparg src) {
    assert(target.getType().equals(Types.FUTURE_STRING));
    assert(src.isImmediateString());
    currBlock().addInstruction(
        TurbineOp.assignString(target, src));
  }

  @Override
  public void retrieveString(Variable target, Variable source) {
    assert(target.getType().equals(Types.VALUE_INTEGER));
    assert(source.getType().equals(Types.FUTURE_INTEGER));
    currBlock().addInstruction(
        TurbineOp.retrieveString(target, source));
  }

  @Override
  public void localArithOp(ArithOpcode op, Variable out, 
                                            List<Oparg> in) {
    if (out != null) {
      assert(Types.isScalarValue(out.getType()));
    }
    currBlock().addInstruction(new LocalArithOp(op, out, in));
  }

  @Override
  public void structLookup(Variable structVar, String structField,
      Variable result) {
    assert(Types.isStruct(structVar.getType()));
    assert(result.getStorage() == VariableStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.structLookup(result, structVar, structField));

  }
  
  @Override
  public void structRefLookup(Variable structVar, String structField,
      Variable result) {
    assert(Types.isStructRef(structVar.getType()));
    assert(Types.isReference(result.getType()));
    assert(result.getStorage() != VariableStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.structRefLookup(result, structVar, structField));
  }

  @Override
  public void structClose(Variable struct) {
    currBlock().addInstruction(
        TurbineOp.structClose(struct));
  }

  @Override
  public void structInsert(Variable structVar, String fieldName,
      Variable fieldContents) {
    currBlock().addInstruction(
        TurbineOp.structInsert(structVar, fieldName, fieldContents));

  }

  @Override
  public void addGlobal(String name, Oparg val) {
    assert(val.isConstant() ||
        (Types.isScalarValue(val.getVariable().getType())));
    program.addGlobalConst(name, val);
  }

  @Override
  public void initUpdateable(Variable updateable, Oparg val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    if (!updateable.getType().equals(Types.UPDATEABLE_FLOAT)) {
      throw new ParserRuntimeException(updateable.getType() +
          " not yet supported");
    }
    assert(val.isImmediateFloat());
    
    currBlock().addInstruction(TurbineOp.initUpdateableFloat(updateable, val));
  }
  
  @Override
  public void latestValue(Variable result, Variable updateable) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    assert(Types.isScalarValue(result.getType()));
    assert(updateable.getType().getPrimitiveType() ==
                  result.getType().getPrimitiveType());
    currBlock().addInstruction(
          TurbineOp.latestValue(result, updateable));
  }

  @Override
  public void update(Variable updateable, UpdateMode updateMode, Variable val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    assert(Types.isScalarFuture(val.getType()));
    assert(updateable.getType().getPrimitiveType() ==
                             val.getType().getPrimitiveType());
    assert(updateMode != null);
    
    currBlock().addInstruction(
          TurbineOp.update(updateable, updateMode, val));
  }
  
  @Override
  public void updateImm(Variable updateable, UpdateMode updateMode,
                                                Oparg val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    if (updateable.getType().equals(Types.UPDATEABLE_FLOAT)) {
      assert(val.isImmediateFloat());
    } else {
      throw new ParserRuntimeException("only updateable floats are"
          + " implemented so far");
    }
    assert(updateMode != null);
    
    currBlock().addInstruction(
          TurbineOp.updateImm(updateable, updateMode, val));
  }

}
