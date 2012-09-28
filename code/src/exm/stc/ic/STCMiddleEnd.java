package exm.stc.ic;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.FunctionSemantics.TclOpTemplate;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Variable.DefType;
import exm.stc.common.lang.Variable.VariableStorage;
import exm.stc.ic.opt.ICOptimiser;
import exm.stc.ic.tree.ICContinuations.ForeachLoop;
import exm.stc.ic.tree.ICContinuations.IfStatement;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.NestedBlock;
import exm.stc.ic.tree.ICContinuations.RangeLoop;
import exm.stc.ic.tree.ICContinuations.SwitchStatement;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Comment;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.LocalFunctionCall;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICInstructions.RunExternal;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.BuiltinFunction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * This class can be used to create the intermediate representation for a 
 * program.  The intermediate representation is built up by calling methods
 * on this class in sequence.  Once the IR is built up, it can be optimised,
 * or it can be "replayed" with the regenerate method in order to 
 * do the final code generation.
 */
public class STCMiddleEnd implements CompilerBackend {

  private final Logger logger;
  private Program program;

  // Keep track of current place in program
  private Function currFunction = null;
  private final Deque<Block> blockStack = new ArrayDeque<Block>();
  
  private final Deque<Loop> loopStack = new ArrayDeque<Loop>();
  
//Place to log IC to (can be null for no output)
  private PrintStream icOutput;

  private Block currBlock() {
    return blockStack.peek();
  }

  public STCMiddleEnd(Logger logger, PrintStream icOutput) {
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
    this.program = ICOptimiser.optimise(logger, icOutput, program);
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
                                    FunctionType fType, 
                                    TclOpTemplate inlineTclTemplate)
  throws UserException
  {
    assert(blockStack.size() == 0);
    assert(currFunction == null);
    BuiltinFunction bf =
        new BuiltinFunction(name, pkg, version, symbol, fType, inlineTclTemplate);
    program.addBuiltin(bf);
  }

  @Override
  public void startFunction(String functionName, List<Variable> oList,
      List<Variable> iList, TaskMode mode) throws UserException {
    assert(blockStack.size() == 0);
    assert(currFunction == null);
    currFunction = new Function(functionName, iList, oList, mode);
    program.addFunction(currFunction);
    blockStack.add(currFunction.getMainblock());
  }

  @Override
  public void endFunction() {
    assert(currFunction != null);
    assert(blockStack.size() == 1);

    currFunction = null;
    blockStack.pop();
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
  public void startIfStatement(Arg condition, boolean hasElse) {
    assert(currFunction != null);
    assert(condition.getSwiftType().equals(Types.VALUE_INTEGER)
          || condition.getSwiftType().equals(Types.VALUE_BOOLEAN));

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
      List<Variable> usedVariables, List<Variable> keepOpenVars,
      boolean explicit, TaskMode mode) {
    assert(currFunction != null);
    WaitStatement wait = new WaitStatement(procName, waitVars, usedVariables,
                                          keepOpenVars, explicit, mode);
    currBlock().addContinuation(wait);
    blockStack.push(wait.getBlock());
  }

  @Override
  public void endWaitStatement(List<Variable> keepOpenVars) {
    assert(currBlock().getType() == BlockType.WAIT_BLOCK);
    blockStack.pop();
  }

  @Override
  public void startSwitch(Arg switchVar,
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
         List<Variable> usedVariables, List<Variable> keepOpenVars) {
    if(!Types.isArray(arrayVar.getType())) {
      throw new STCRuntimeError("foreach loop over non-array: " + 
                arrayVar.toString()); 
    }
    assert(arrayVar.getType().getMemberType().equals(memberVar.getType()));
    assert(loopCountVar == null || 
              loopCountVar.getType().equals(Types.VALUE_INTEGER));
    ForeachLoop loop = new ForeachLoop(arrayVar, memberVar, 
                loopCountVar, isSync, splitDegree, arrayClosed, usedVariables, 
                keepOpenVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endForeachLoop(boolean isSync, int splitDegree, 
            boolean arrayClosed, List<Variable> keepOpenVars) {
    assert(blockStack.peek().getType() == BlockType.FOREACH_BODY);
    blockStack.pop();
  }

  @Override
  public void startRangeLoop(String loopName, Variable loopVar,
      Arg start, Arg end, Arg increment, boolean isSync,
      List<Variable> usedVariables, List<Variable> keepOpenVars,
      int desiredUnroll, int splitDegree) {
    RangeLoop loop = new RangeLoop(loopName, loopVar, start, end, increment,
                                isSync, usedVariables, keepOpenVars,
                                desiredUnroll, splitDegree);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endRangeLoop(boolean isSync, 
                          List<Variable> keepOpenVars,
                          int splitDegree) {
    assert(currBlock().getType() == BlockType.RANGELOOP_BODY);
    blockStack.pop();
  }

  @Override
  public void startLoop(String loopName, List<Variable> loopVars,
      List<Variable> initVals, List<Variable> usedVariables,
      List<Variable> keepOpenVars, List<Boolean> blockingVars) {
    Loop loop = new Loop(loopName, loopVars, initVals,
        usedVariables, keepOpenVars, blockingVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
    loopStack.push(loop);
  }

  @Override
  public void loopContinue(List<Variable> newVals, 
        List<Variable> usedVariables, List<Variable> keepOpenVars,
        List<Boolean> blockingVars) {
    LoopContinue inst = new LoopContinue(newVals, usedVariables,
        keepOpenVars, blockingVars);
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopContinue(inst);
  }

  @Override
  public void loopBreak(List<Variable> closeVars) {
    LoopBreak inst = new LoopBreak(
        new ArrayList<Variable>(closeVars));
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
      DefType defType, Variable mapping)
      throws UndefinedTypeException {
    assert(mapping == null || Types.isMappable(type));
    assert(mapping == null || Types.isString(mapping.getType()));
    currBlock().declareVariable(type, name, storage, defType, mapping);
  }


  @Override
  public void builtinFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, Arg priority) {
    assert(priority == null || priority.isImmediateInt());
    currBlock().addInstruction(
        FunctionCall.createBuiltinCall(
            function, inputs, outputs, priority));
  }
  
  @Override
  public void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Variable> outputs) {
    currBlock().addInstruction(new LocalFunctionCall(functionName,
            inputs, outputs));
  }
  
  @Override
  public void functionCall(String function, List<Variable> inputs,
      List<Variable> outputs, List<Boolean> blockOn, TaskMode mode, 
      Arg priority) {
    assert(priority == null || priority.isImmediateInt());
    if (blockOn != null) {
      throw new STCRuntimeError("Swift IC generator doesn't support " +
      		" blocking on function inputs");
    }
    currBlock().addInstruction(
          FunctionCall.createFunctionCall(
              function, inputs, outputs, mode, priority));
  }

  @Override
  public void runExternal(String cmd, List<Arg> inputs, List<Variable> outputs,
                          List<ExtArgType> order,
                          boolean hasSideEffects, boolean deterministic) {
    for (Variable o: outputs) {
      assert(Types.isFile(o.getType()));
    }
    assert(inputs.size() + outputs.size() == order.size());
    
    currBlock().addInstruction(new RunExternal(cmd, outputs, inputs,
                                order, hasSideEffects, deterministic));
        
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
      Arg arrIx, boolean isArrayRef) {
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
      Arg arrIx) {
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
      Arg arrIx) {
    assert(arrIx.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.arrayInsertImm(iVar, arrayVar, arrIx));
  }
  
  @Override
  public void arrayRefInsertImm(Variable iVar, Variable arrayVar,
          Arg arrIx, Variable outerArrayVar) {
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
      Variable arrayVar, Arg arrIx) {
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
      Variable arrayVar, Arg arrIx) {
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
  public void dereferenceFile(Variable target, Variable src) {
    assert(target.getType().equals(Types.FUTURE_FILE));
    assert(src.getType().equals(Types.REFERENCE_FILE));
    currBlock().addInstruction(
        TurbineOp.dereferenceFile(target, src));
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
  public void assignInt(Variable target, Arg src) {
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
  public void assignBool(Variable target, Arg src) {
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
  public void assignFloat(Variable target, Arg src) {
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
  public void assignString(Variable target, Arg src) {
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
  public void localOp(BuiltinOpcode op, Variable out, 
                                            List<Arg> in) {
    if (out != null) {
      assert(Types.isScalarValue(out.getType()));
    }
    currBlock().addInstruction(Builtin.createLocal(op, out, in));
  }
  
  @Override
  public void asyncOp(BuiltinOpcode op, Variable out, 
                                    List<Arg> in, Arg priority) {
    if (out != null) {
      assert(Types.isScalarFuture(out.getType()));
    }
    currBlock().addInstruction(Builtin.createAsync(op, out, in, priority));
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
  public void addGlobal(String name, Arg val) {
    assert(val.isConstant() ||
        (Types.isScalarValue(val.getVar().getType())));
    program.addGlobalConst(name, val);
  }

  @Override
  public void initUpdateable(Variable updateable, Arg val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    if (!updateable.getType().equals(Types.UPDATEABLE_FLOAT)) {
      throw new STCRuntimeError(updateable.getType() +
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
  public void update(Variable updateable, Operators.UpdateMode updateMode, Variable val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    assert(Types.isScalarFuture(val.getType()));
    assert(updateable.getType().getPrimitiveType() ==
                             val.getType().getPrimitiveType());
    assert(updateMode != null);
    
    currBlock().addInstruction(
          TurbineOp.update(updateable, updateMode, val));
  }
  
  @Override
  public void updateImm(Variable updateable, Operators.UpdateMode updateMode,
                                                Arg val) {
    assert(Types.isScalarUpdateable(updateable.getType()));
    if (updateable.getType().equals(Types.UPDATEABLE_FLOAT)) {
      assert(val.isImmediateFloat());
    } else {
      throw new STCRuntimeError("only updateable floats are"
          + " implemented so far");
    }
    assert(updateMode != null);
    
    currBlock().addInstruction(
          TurbineOp.updateImm(updateable, updateMode, val));
  }

  @Override
  public void getFileName(Variable filename, Variable file) {
    assert(Types.isString(filename.getType()));
    assert(filename.getStorage() == VariableStorage.ALIAS);
    assert(Types.isFile(file.getType()));
    currBlock().addInstruction(
            TurbineOp.getFileName(filename, file));
  }

}
