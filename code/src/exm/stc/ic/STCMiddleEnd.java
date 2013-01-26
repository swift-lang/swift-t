/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.ic;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.TclFunRef;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.opt.ICOptimizer;
import exm.stc.ic.tree.ICContinuations.ForeachLoop;
import exm.stc.ic.tree.ICContinuations.IfStatement;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.NestedBlock;
import exm.stc.ic.tree.ICContinuations.RangeLoop;
import exm.stc.ic.tree.ICContinuations.SwitchStatement;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Comment;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
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
  public void optimize() throws UserException {
    logger.debug("Optimising Swift IC");
    this.program = ICOptimizer.optimize(logger, icOutput, program);
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
  public void requirePackage(String pkg, String version) {
    program.addRequiredPackage(pkg, version);
  }
  
  @Override
  public void defineBuiltinFunction(String name,
                                    FunctionType fType,
                                    TclFunRef impl)
  throws UserException
  {
    assert(blockStack.size() == 0);
    assert(currFunction == null);
    BuiltinFunction bf = new BuiltinFunction(name, fType, impl);
    program.addBuiltin(bf);
  }

  @Override
  public void startFunction(String functionName, List<Var> oList,
      List<Var> iList, TaskMode mode) throws UserException {
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
    assert(condition.getType().equals(Types.V_INT)
          || condition.getType().equals(Types.V_BOOL));

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
  public void startWaitStatement(String procName, List<Var> waitVars,
      List<Var> usedVariables, List<Var> keepOpenVars, Arg priority,
      WaitMode mode, boolean recursive, TaskMode target) {
    assert(currFunction != null);
    assert(priority == null || priority.isImmediateInt());
    
    WaitStatement wait = new WaitStatement(procName, waitVars, usedVariables,
                            keepOpenVars, priority, mode, recursive, target);
    currBlock().addContinuation(wait);
    blockStack.push(wait.getBlock());
  }

  @Override
  public void endWaitStatement(List<Var> waitVars, List<Var> usedVars,
                               List<Var> keepOpenVars) {
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
  public void startForeachLoop(String loopName,
          Var arrayVar, Var memberVar, Var loopCountVar, 
          int splitDegree, boolean arrayClosed,
          List<Var> usedVariables, List<Var> keepOpenVars) {
    if(!Types.isArray(arrayVar.type())) {
      throw new STCRuntimeError("foreach loop over non-array: " + 
                arrayVar.toString()); 
    }
    assert(arrayVar.type().memberType().equals(memberVar.type()));
    assert(loopCountVar == null || 
              loopCountVar.type().equals(Types.V_INT));
    ForeachLoop loop = new ForeachLoop(loopName,
            arrayVar, memberVar, loopCountVar, splitDegree,
            arrayClosed, usedVariables, keepOpenVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endForeachLoop(int splitDegree, 
            boolean arrayClosed, List<Var> usedVars,
            List<Var> keepOpenVars) {
    assert(blockStack.peek().getType() == BlockType.FOREACH_BODY);
    blockStack.pop();
  }

  @Override
  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment,
      List<Var> usedVariables, List<Var> keepOpenVars,
      int desiredUnroll, int splitDegree) {
    RangeLoop loop = new RangeLoop(loopName, loopVar, countVar,
                                start, end, increment,
                                usedVariables, keepOpenVars,
                                desiredUnroll, splitDegree);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  @Override
  public void endRangeLoop(List<Var> usedVars, List<Var> keepOpenVars,
                          int splitDegree) {
    assert(currBlock().getType() == BlockType.RANGELOOP_BODY);
    blockStack.pop();
  }

  @Override
  public void startLoop(String loopName, List<Var> loopVars,
      List<Boolean> definedHere, List<Var> initVals, List<Var> usedVariables,
      List<Var> keepOpenVars, List<Boolean> blockingVars) {
    Loop loop = new Loop(loopName, loopVars, definedHere, initVals,
        usedVariables, keepOpenVars, blockingVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
    loopStack.push(loop);
  }

  @Override
  public void loopContinue(List<Var> newVals, 
        List<Var> usedVariables, List<Var> keepOpenVars,
        List<Boolean> blockingVars) {
    LoopContinue inst = new LoopContinue(newVals, usedVariables,
        keepOpenVars, blockingVars);
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopContinue(inst);
  }

  @Override
  public void loopBreak(List<Var> loopUsedVars, List<Var> closeVars) {
    LoopBreak inst = new LoopBreak(loopUsedVars, closeVars);
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
  public void declare(Type type, String name, VarStorage storage,
      DefType defType, Var mapping)
      throws UndefinedTypeException {
    assert(mapping == null || Types.isMappable(type));
    assert(mapping == null || Types.isString(mapping.type()));
    currBlock().declareVariable(type, name, storage, defType, mapping);
  }


  @Override
  public void builtinFunctionCall(String function, List<Var> inputs,
      List<Var> outputs, Arg priority) {
    assert(priority == null || priority.isImmediateInt());
    currBlock().addInstruction(
        FunctionCall.createBuiltinCall(
            function, inputs, outputs, priority));
  }
  
  @Override
  public void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Var> outputs) {
    currBlock().addInstruction(new LocalFunctionCall(functionName,
            inputs, outputs));
  }
  
  @Override
  public void functionCall(String function, List<Var> inputs,
      List<Var> outputs, List<Boolean> blockOn, TaskMode mode, 
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
  public void runExternal(String cmd, List<Arg> args, List<Arg> inFiles,
                          List<Var> outFiles, List<Arg> outFileNames,
                          Redirects<Arg> redirects,
                          boolean hasSideEffects, boolean deterministic) {
    for (Var o: outFiles) {
      assert(o.type().assignableTo(Types.V_FILE) 
              || o.type().assignableTo(Types.V_VOID));
    }
    assert(outFiles.size() == outFileNames.size());
    for (Arg o: outFileNames) {
      assert(o == null || o.getType().assignableTo(Types.V_STRING));
    }
    
    for (Arg i: inFiles) {
      assert(i.getType().assignableTo(Types.V_FILE));
    }
    

    currBlock().addInstruction(new RunExternal(cmd, inFiles, outFiles, 
        outFileNames, args, redirects, hasSideEffects, deterministic));
  }

  @Override
  public void decrWriters(Var arr) {
    assert(Types.isArray(arr.type()));
    currBlock().addCleanup(arr, TurbineOp.arrayDecrWriters(arr));
  }

  @Override
  public void decrRef(Var v) {
    currBlock().addCleanup(v, TurbineOp.decrRef(v));
  }
  
  @Override
  public void arrayLookupFuture(Var oVar, Var arrayVar,
      Var indexVar, boolean isArrayRef) {
    assert(Types.isInt(indexVar.type()));
    if (isArrayRef) {
      currBlock().addInstruction(
          TurbineOp.arrayRefLookupFuture(oVar, arrayVar, indexVar));
    } else {
      currBlock().addInstruction(
          TurbineOp.arrayLookupFuture(oVar, arrayVar, indexVar));
    }
  }

  @Override
  public void arrayLookupRefImm(Var oVar, Var arrayVar,
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
  public void arrayLookupImm(Var oVar, Var arrayVar,
      Arg arrIx) {
    assert(oVar.storage() == VarStorage.ALIAS);
    assert(Types.isArray(arrayVar.type())); // Can't be reference to array
    assert(arrIx.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.arrayLookupImm(oVar, arrayVar, arrIx));
  }

  @Override
  public void arrayInsertFuture(Var iVar, Var arrayVar,
      Var indexVar) {
    assert(Types.isInt(indexVar.type()));
    currBlock().addInstruction(
        TurbineOp.arrayInsertFuture(iVar, arrayVar, indexVar));
  }

  @Override
  public void arrayRefInsertFuture(Var iVar,
      Var arrayVar, Var indexVar, Var outerArrayVar) {
    assert(Types.isInt(indexVar.type()));
    assert(Types.isArrayRef(arrayVar.type()));
    currBlock().addInstruction(
        TurbineOp.arrayRefInsertFuture(iVar, arrayVar, indexVar, 
                                              outerArrayVar));
  }
  
  @Override
  public void arrayInsertImm(Var iVar, Var arrayVar,
      Arg arrIx) {
    assert(arrIx.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.arrayInsertImm(iVar, arrayVar, arrIx));
  }
  
  @Override
  public void arrayRefInsertImm(Var iVar, Var arrayVar,
          Arg arrIx, Var outerArrayVar) {
    assert(arrIx.isImmediateInt());
    assert(Types.isArrayRef(arrayVar.type()));
    currBlock().addInstruction(
        TurbineOp.arrayRefInsertImm(iVar, arrayVar, arrIx, outerArrayVar));
  }

  @Override
  public void arrayCreateNestedFuture(Var arrayResult,
      Var arrayVar, Var indexVar) {
    assert(Types.isArrayRef(arrayResult.type()));
    assert(Types.isArray(arrayVar.type()));
    assert(Types.isInt(indexVar.type()));

    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedComputed(arrayResult,
          arrayVar, indexVar));
  }

  @Override
  public void arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArray(arrayResult.type()));
    assert(Types.isArray(arrayVar.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);
    assert(arrIx.isImmediateInt());

    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedImm(arrayResult,
          arrayVar, arrIx));
  }

  @Override
  public void arrayRefCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArrayRef(arrayResult.type()));
    assert(Types.isArrayRef(arrayVar.type()));
    assert(arrIx.isImmediateInt());

    currBlock().addInstruction(
      TurbineOp.arrayRefCreateNestedImmIx(arrayResult, arrayVar, arrIx));
  }

  @Override
  public void arrayRefCreateNestedFuture(Var arrayResult,
      Var arrayVar, Var indexVar) {
    assert(Types.isArrayRef(arrayResult.type()));
    assert(Types.isArrayRef(arrayVar.type()));
    assert(Types.isInt(indexVar.type()));

    currBlock().addInstruction(
      TurbineOp.arrayRefCreateNestedComputed(arrayResult,
          arrayVar, indexVar));

  }

  @Override
  public void assignReference(Var target, Var src) {
    currBlock().addInstruction(
        TurbineOp.addressOf(target, src));
  }


  @Override
  public void dereferenceInt(Var target, Var src) {
    assert(Types.isInt(target.type()));
    assert(src.type().equals(Types.R_INT));
    currBlock().addInstruction(
        TurbineOp.dereferenceInt(target, src));
  }
  
  @Override
  public void dereferenceBool(Var target, Var src) {
    assert(Types.isBool(target.type()));
    assert(src.type().equals(Types.R_BOOL));
    currBlock().addInstruction(
        TurbineOp.dereferenceBool(target, src));
  }

  @Override
  public void dereferenceFloat(Var target, Var src) {
    assert(Types.isFloat(target.type()));
    assert(src.type().equals(Types.R_FLOAT));
    currBlock().addInstruction(
        TurbineOp.dereferenceFloat(target, src));
  }

  @Override
  public void dereferenceString(Var target, Var src) {
    assert(Types.isString(target.type()));
    assert(src.type().equals(Types.R_STRING));
    currBlock().addInstruction(
        TurbineOp.dereferenceString(target, src));
  }

  @Override
  public void dereferenceBlob(Var target, Var src) {
    assert(Types.isBlob(target.type()));
    assert(src.type().equals(Types.R_BLOB));
    currBlock().addInstruction(
        TurbineOp.dereferenceBlob(target, src));
  }

  @Override
  public void dereferenceFile(Var target, Var src) {
    assert(Types.isFile(target.type()));
    assert(src.type().equals(Types.REF_FILE));
    currBlock().addInstruction(
        TurbineOp.dereferenceFile(target, src));
  }
  
  @Override
  public void retrieveRef(Var target, Var src) {
    assert(Types.isRef(src.type()));
    assert(Types.isRefTo(src.type(), target.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveRef(target, src));

  }
  
  @Override
  public void makeAlias(Var dst, Var src) {
    assert(src.type().equals(dst.type()));
    assert(dst.storage() == VarStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.copyRef(dst, src));
  }

  @Override
  public void assignInt(Var target, Arg src) {
    assert(Types.isInt(target.type()));
    assert(src.isImmediateInt());
    currBlock().addInstruction(
        TurbineOp.assignInt(target, src));
  }

  @Override
  public void retrieveInt(Var target, Var source) {
    assert(target.type().equals(Types.V_INT));
    assert(Types.isInt(source.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveInt(target, source));
  }

  @Override
  public void assignBool(Var target, Arg src) {
    assert(Types.isBool(target.type()));
    assert(src.isImmediateBool());
    currBlock().addInstruction(
        TurbineOp.assignBool(target, src));
  }

  @Override
  public void retrieveBool(Var target, Var source) {
    assert(target.type().equals(Types.V_BOOL));
    assert(Types.isBool(source.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveBool(target, source));
  }
  
  @Override
  public void assignVoid(Var target, Arg src) {
    assert(Types.isVoid(target.type()));
    assert(src.getType().equals(Types.V_VOID));
    currBlock().addInstruction(TurbineOp.assignVoid(target, src));
  }

  @Override
  public void retrieveVoid(Var target, Var source) {
    assert(target.type().equals(Types.V_VOID));
    assert(Types.isVoid(source.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveVoid(target, source));
  }

  @Override
  public void assignFloat(Var target, Arg src) {
    assert(Types.isFloat(target.type()));
    assert(src.isImmediateFloat());
    currBlock().addInstruction(
        TurbineOp.assignFloat(target, src));
  }

  @Override
  public void retrieveFloat(Var target, Var source) {
    assert(target.type().equals(Types.V_FLOAT));
    assert(Types.isFloat(source.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveFloat(target, source));
  }

  @Override
  public void assignString(Var target, Arg src) {
    assert(Types.isString(target.type()));
    assert(src.isImmediateString());
    currBlock().addInstruction(
        TurbineOp.assignString(target, src));
  }

  @Override
  public void retrieveString(Var target, Var source) {
    assert(target.type().equals(Types.V_STRING));
    assert(Types.isString(source.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveString(target, source));
  }
  
  @Override
  public void assignBlob(Var target, Arg src) {
    assert(Types.isBlob(target.type()));
    assert(src.isImmediateBlob());
    currBlock().addInstruction(
        TurbineOp.assignBlob(target, src));
  }
  
  @Override
  public void retrieveBlob(Var target, Var src) {
    assert(target.type().equals(Types.V_BLOB));
    assert(Types.isBlob(src.type()));
    currBlock().addInstruction(
        TurbineOp.retrieveBlob(target, src));
  }
  
  @Override
  public void decrBlobRef(Var blob) {
    assert(Types.isBlob(blob.type()));
    currBlock().addInstruction(TurbineOp.decrBlobRef(blob));
  }
  
  @Override
  public void freeBlob(Var blobVal) {
    assert(blobVal.type().equals(Types.V_BLOB));
    currBlock().addInstruction(TurbineOp.freeBlob(blobVal));
  }


  @Override
  public void assignFile(Var target, Arg src) {
    assert(Types.isFile(target.type()));
    assert(src.isVar());
    assert(src.getVar().type().assignableTo(Types.V_FILE));
    currBlock().addInstruction(TurbineOp.assignFile(target, src));
  }
  

  @Override
  public void retrieveFile(Var target, Var src) {
    assert(Types.isFile(src.type()));
    assert(target.type().assignableTo(Types.V_FILE));
    currBlock().addInstruction(TurbineOp.retrieveFile(target, src));
  }
  
  @Override
  public void decrLocalFileRef(Var fileVal) {
    assert(fileVal.type().assignableTo(Types.V_FILE));
    currBlock().addCleanup(fileVal, TurbineOp.decrLocalFileRef(fileVal));
  }
  
  @Override
  public void localOp(BuiltinOpcode op, Var out, 
                                            List<Arg> in) {
    if (out != null) {
      assert(Types.isScalarValue(out.type()));
    }
    currBlock().addInstruction(Builtin.createLocal(op, out, in));
  }
  
  @Override
  public void asyncOp(BuiltinOpcode op, Var out, 
                                    List<Arg> in, Arg priority) {
    if (out != null) {
      assert(Types.isScalarFuture(out.type()));
    }
    currBlock().addInstruction(Builtin.createAsync(op, out, in, priority));
  }

  @Override
  public void structLookup(Var structVar, String structField,
      Var result) {
    assert(Types.isStruct(structVar.type()));
    assert(result.storage() == VarStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.structLookup(result, structVar, structField));

  }
  
  @Override
  public void structRefLookup(Var structVar, String structField,
      Var result) {
    assert(Types.isStructRef(structVar.type()));
    assert(Types.isRef(result.type()));
    assert(result.storage() != VarStorage.ALIAS);
    currBlock().addInstruction(
        TurbineOp.structRefLookup(result, structVar, structField));
  }

  @Override
  public void structClose(Var struct) {
    currBlock().addInstruction(
        TurbineOp.structClose(struct));
  }

  @Override
  public void structInsert(Var structVar, String fieldName,
      Var fieldContents) {
    currBlock().addInstruction(
        TurbineOp.structInsert(structVar, fieldName, fieldContents));

  }

  /**
     TODO: Handle updateable globals
   */
  @Override
  public void addGlobal(String name, Arg val) {
    assert(val.isConstant() ||
        (Types.isScalarValue(val.getVar().type())));
    program.addGlobalConst(name, val);
  }

  @Override
  public void initUpdateable(Var updateable, Arg val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type() +
          " not yet supported");
    }
    assert(val.isImmediateFloat());
    
    currBlock().addInstruction(TurbineOp.initUpdateableFloat(updateable, val));
  }
  
  @Override
  public void latestValue(Var result, Var updateable) {
    assert(Types.isScalarUpdateable(updateable.type()));
    assert(Types.isScalarValue(result.type()));
    assert(updateable.type().primType() ==
                  result.type().primType());
    currBlock().addInstruction(
          TurbineOp.latestValue(result, updateable));
  }

  @Override
  public void update(Var updateable, Operators.UpdateMode updateMode, Var val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    assert(Types.isScalarFuture(val.type()));
    assert(updateable.type().primType() ==
                             val.type().primType());
    assert(updateMode != null);
    
    currBlock().addInstruction(
          TurbineOp.update(updateable, updateMode, val));
  }
  
  @Override
  public void updateImm(Var updateable, Operators.UpdateMode updateMode,
                                                Arg val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    if (updateable.type().equals(Types.UP_FLOAT)) {
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
  public void getFileName(Var filename, Var file,
                          boolean initUnmapped) {
    assert(Types.isString(filename.type()));
    assert(filename.storage() == VarStorage.ALIAS);
    assert(Types.isFile(file.type()));
    currBlock().addInstruction(
            TurbineOp.getFileName(filename, file, initUnmapped));
  }
  
  @Override
  public void chooseTmpFilename(Var filenameVal) {
    assert(filenameVal.type().assignableTo(Types.V_STRING));
    currBlock().addInstruction(
            TurbineOp.chooseTmpFilename(filenameVal));
  }

  @Override
  public void generateWrappedBuiltin(String function, FunctionType ft,
      List<Var> outArgs, List<Var> inArgs, TaskMode mode) throws UserException {
    Function fn = new Function(function, inArgs, outArgs, TaskMode.SYNC);
    this.program.addFunction(fn);
    
    List<Var> used = new ArrayList<Var>();
    used.addAll(inArgs);
    used.addAll(outArgs);

    WaitMode waitMode;
    if (mode == TaskMode.LOCAL || mode == TaskMode.SYNC) {
      // Cases where function can execute on any node
      waitMode = WaitMode.DATA_ONLY;
    } else {
      // Cases where we may need to send task to another class of worker
      waitMode = WaitMode.TASK_DISPATCH;
    }
    
    WaitStatement wait = new WaitStatement(function + "-argwait",
                  inArgs, used, Collections.<Var>emptyList(), null,
                  waitMode, true, mode);
    
    fn.getMainblock().addContinuation(wait);
    Block block = wait.getBlock();
    
    List<Instruction> instBuffer = new ArrayList<Instruction>();
    List<Arg> inVals = new ArrayList<Arg>();
    for (Var inArg: inArgs) {
      inVals.add(Arg.createVar(WrapUtil.fetchValueOf(block, instBuffer,
              inArg, Var.LOCAL_VALUE_VAR_PREFIX + inArg.name())));
    }
    List<Var> outVals = new ArrayList<Var>();
    for (Var outArg: outArgs) {
      outVals.add(WrapUtil.declareLocalOutputVar(block, outArg,
                  Var.LOCAL_VALUE_VAR_PREFIX + outArg.name()));
    }
    instBuffer.add(new LocalFunctionCall(function, inVals, outVals));
    
    for (int i = 0; i < outVals.size(); i++) {
      Var outArg = outArgs.get(i);
      Var outVal = outVals.get(i);
      instBuffer.add(ICInstructions.futureSet(outArg, Arg.createVar(outVal)));
    }
    
    block.addInstructions(instBuffer);
  }

}
