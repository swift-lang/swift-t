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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.RefCount;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.LocalForeignFunction;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RequiredPackage;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.NestedContainerInfo;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.lang.WrappedForeignFunction;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.ICOptimizer;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.Conditionals.SwitchStatement;
import exm.stc.ic.tree.ForeachLoops.ForeachLoop;
import exm.stc.ic.tree.ForeachLoops.RangeLoop;
import exm.stc.ic.tree.ICContinuations.AsyncExec;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.NestedBlock;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Comment;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.LocalFunctionCall;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICInstructions.ExecExternal;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.BuiltinFunction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.TurbineOp;

/**
 * This class can be used to create the intermediate representation for a
 * program.  The intermediate representation is built up by calling methods
 * on this class in body.  Once the IR is built up, it can be optimised,
 * or it can be "replayed" with the regenerate method in order to
 * do the final code generation.
 */
public class STCMiddleEnd {

  private final Logger logger;
  private Program program;

  // Keep track of current place in program
  private Function currFunction = null;
  private final StackLite<Block> blockStack = new StackLite<Block>();

  private final StackLite<Loop> loopStack = new StackLite<Loop>();

//Place to log IC to (can be null for no output)
  private PrintStream icOutput;

  private Block currBlock() {
    return blockStack.peek();
  }

  public STCMiddleEnd(Logger logger, PrintStream icOutput, ForeignFunctions foreignFuncs) {
    this.logger = logger;
    this.program = new Program(foreignFuncs);
    this.icOutput = icOutput;

    initDefaults();
  }

  private void initDefaults() {
    this.program.constants().add(Var.NO_WAIT_STRING_VAR,
                         Arg.newString("nowait"));
  }

  public void optimize() throws UserException {
    logger.debug("Optimising Swift IC");
    // System.out.println(program);
    program = ICOptimizer.optimize(logger, icOutput, program);
    // System.out.println(program);
    logger.debug("Optimisation done");
  }

  /**
   * Recreate an equivalent series of calls that were used
   * to create the program
   * @throws UserException
   */
  public void regenerate(CompilerBackend backend) throws UserException {
    logger.debug("Using Swift IC to regenerate code");
    this.program.generate(logger, backend);
    logger.debug("Done using Swift IC to regenerate code");
  }

  public void requirePackage(RequiredPackage pkg) {
    program.addRequiredPackage(pkg);
  }

  public void defineStructType(StructType newType) {
    program.addStructType(newType);
  }

  public void declareWorkType(WorkContext workType) {
    program.addWorkType(workType);
  }

  /**
   *
   * @param fid
   * @param fType
   * @param localImpl can be null
   * @param wrappedImpl can be null
   * @throws UserException
   */
  public void defineBuiltinFunction(FnID fid, FunctionType fType,
      LocalForeignFunction localImpl, WrappedForeignFunction wrappedImpl)
  throws UserException {
    assert(blockStack.size() == 0);
    assert(currFunction == null);
    BuiltinFunction bf = new BuiltinFunction(fid, fType, localImpl, wrappedImpl);
    program.addBuiltin(bf);
  }

  public void startFunction(FnID id, List<Var> oList,
      List<Var> iList, ExecTarget mode) throws UserException {
    assert(blockStack.size() == 0);
    assert(currFunction == null);
    currFunction = new Function(id, iList, oList, mode);
    program.addFunction(currFunction);
    blockStack.add(currFunction.mainBlock());
  }

  public void endFunction() {
    assert(currFunction != null);
    assert(blockStack.size() == 1) : blockStack.size();

    currFunction = null;
    blockStack.pop();
  }

  public void startNestedBlock() {
    NestedBlock b = new NestedBlock();
    currBlock().addContinuation(b);
    blockStack.push(b.getBlock());
  }

  public void endNestedBlock() {
    assert(blockStack.size() >= 2);
    assert(currBlock().getType() == BlockType.NESTED_BLOCK);
    blockStack.pop();
  }

  public void addComment(String comment) {
    currBlock().addInstruction(new Comment(comment));
  }

  public void startIfStatement(Arg condition, boolean hasElse) {
    assert(currFunction != null);
    assert(Types.isIntVal(condition) || Types.isBoolVal(condition));

    IfStatement stmt = new IfStatement(condition);
    currBlock().addStatement(stmt);

    if (hasElse) {
      blockStack.push(stmt.elseBlock());
    }

    blockStack.push(stmt.thenBlock());
  }

  public void startElseBlock() {
    // Should still be then, else and top level procedure
    assert(blockStack.size() >= 3);
    assert(currBlock().getType() == BlockType.THEN_BLOCK);
    blockStack.pop();
  }

  public void endIfStatement() {
    // Should still be enclosing block
    assert(blockStack.size() >= 1);
    assert(currBlock().getType() == BlockType.ELSE_BLOCK ||
        currBlock().getType() == BlockType.THEN_BLOCK);
    blockStack.pop();
  }

  /**
   * With default task props
   * @param procName
   * @param waitVars
   * @param mode
   * @param explicit
   * @param recursive
   * @param target
   */
  public void startWaitStatement(String procName, List<Var> waitVars,
      WaitMode mode, boolean explicit, boolean recursive, ExecTarget target) {
    startWaitStatement(procName, waitVars, mode, explicit, recursive, target,
                        new TaskProps());
  }

  public void startWaitStatement(String procName, List<Var> waitVars,
      WaitMode mode, boolean explicit, boolean recursive, ExecTarget target,
      TaskProps props) {
    startWaitStatement(procName, WaitVar.makeList(waitVars, explicit),
                             mode, recursive, target, props);
  }

  public void startWaitStatement(String procName, List<WaitVar> waitVars,
        WaitMode mode, boolean recursive, ExecTarget target, TaskProps props) {
    assert(currFunction != null);
    props.assertInternalTypesValid();

    WaitStatement wait = new WaitStatement(procName, waitVars,
          PassedVar.NONE, Var.NONE, mode, recursive, target, props);
    currBlock().addContinuation(wait);
    blockStack.push(wait.getBlock());
  }

  public void endWaitStatement() {
    assert(currBlock().getType() == BlockType.WAIT_BLOCK);
    blockStack.pop();
  }

  public void startSwitch(Arg switchVar,
      List<Integer> caseLabels, boolean hasDefault) {

    logger.trace("startSwitch() stack size:" + blockStack.size());
    SwitchStatement sw = new SwitchStatement(switchVar, caseLabels);
    currBlock().addStatement(sw);
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

  public void endCase() {
    logger.trace("endCase() stack size:" + blockStack.size());
    // case enclosing at minimum
    assert(blockStack.size() >= 1);
    assert(currBlock().getType() == BlockType.CASE_BLOCK);
    blockStack.pop();
  }

  public void endSwitch() {
    logger.trace("endSwitch() stack size:" + blockStack.size());
    // all cases should already be off stack, do nothing
  }

  public void startForeachLoop(String loopName,
          Var container, Var memberVar, Var loopCountVar,
          int splitDegree, int leafDegree, boolean arrayClosed) {
    assert(Types.isContainer(container) || Types.isContainerLocal(container)):
          "foreach loop over bad type: " + container.toString();

    assert(Types.isElemValType(container, memberVar)): container + " " + memberVar;
    if (Types.isArray(container)) {
      assert(loopCountVar == null ||
          Types.isArrayKeyVal(container, loopCountVar.asArg()));
    } else {
      assert(Types.isBag(container));
      assert(loopCountVar == null);
    }
    ForeachLoop loop = new ForeachLoop(loopName,
            container, memberVar, loopCountVar, splitDegree, leafDegree,
            arrayClosed, PassedVar.NONE, Var.NONE,
            RefCount.NONE, ArrayListMultimap.<Var, RefCount>create(),
            RefCount.NONE);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  public void endForeachLoop() {
    assert(blockStack.peek().getType() == BlockType.FOREACH_BODY);
    blockStack.pop();
  }

  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment, int desiredUnroll, int splitDegree,
      int leafDegree) {
    RangeLoop loop = new RangeLoop(loopName, loopVar, countVar,
          start, end, increment,
          PassedVar.NONE, Var.NONE, desiredUnroll, false,
          splitDegree, leafDegree, RefCount.NONE,
          ArrayListMultimap.<Var, RefCount>create(), RefCount.NONE);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
  }

  public void endRangeLoop() {
    assert(currBlock().getType() == BlockType.RANGELOOP_BODY);
    blockStack.pop();
  }

  public void startLoop(String loopName, List<Var> loopVars,
      List<Boolean> definedHere, List<Arg> initVals,
      List<Boolean> blockingVars) {
    Loop loop = new Loop(loopName, loopVars, definedHere, initVals,
                         PassedVar.NONE, Var.NONE, blockingVars);
    currBlock().addContinuation(loop);
    blockStack.push(loop.getLoopBody());
    loopStack.push(loop);
  }

  public void loopContinue(List<Arg> newVals,
                           List<Boolean> blockingVars) {
    LoopContinue inst = new LoopContinue(newVals, Var.NONE, blockingVars);
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopContinue(inst);
  }

  public void loopBreak() {
    LoopBreak inst = new LoopBreak(PassedVar.NONE, Var.NONE);
    currBlock().addInstruction(inst);
    loopStack.peek().setLoopBreak(inst);
  }

  public void endLoop() {
    Block b = blockStack.pop();
    loopStack.pop();
    assert(b.getType() == BlockType.LOOP_BODY);
  }

  public void startAsyncExec(String procName,
      AsyncExecutor executor, Arg cmd, List<Var> taskOutputs,
      List<Arg> taskArgs, Map<String, Arg> taskProps,
      boolean hasSideEffects) {

    AsyncExec stmt = new AsyncExec(procName, executor, cmd,
          PassedVar.NONE, Var.NONE,
          taskOutputs, taskArgs, taskProps, hasSideEffects);
    currBlock().addContinuation(stmt);

    blockStack.push(stmt.getBlock());
  }

  public void endAsyncExec() {
    assert(currBlock().getType() == BlockType.ASYNC_EXEC_CONTINUATION);
    blockStack.pop();
  }

  public void declare(Var var) throws UndefinedTypeException {
    assert(!var.mappedDecl()|| Types.isMappable(var));
    assert(var.type().isConcrete()) : var;
    if (var.storage() == Alloc.GLOBAL_VAR) {
      program.globalVars().addVariable(var);
    } else {
      currBlock().addVariable(var);
    }
  }


  /**
   * Call with default properties
   * @param functionName
   * @param inputs
   * @param outputs
   */
  public void builtinFunctionCall(FnID id, List<Var> inputs,
                                   List<Var> outputs) {
    builtinFunctionCall(id, inputs, outputs,
                        new TaskProps());
  }

  public void builtinFunctionCall(FnID id,  List<Var> inputs,
      List<Var> outputs, TaskProps props) {
    props.assertInternalTypesValid();
    currBlock().addInstruction(
        FunctionCall.createBuiltinCall(id,
            outputs, Var.asArgList(inputs), props,
            program.foreignFunctions()));
  }

  public void builtinLocalFunctionCall(FnID id,
          List<Arg> inputs, List<Var> outputs) {
    currBlock().addInstruction(new LocalFunctionCall(
        id, inputs, outputs, program.foreignFunctions()));
  }

  public void functionCall(FnID id,
      List<Arg> inputs, List<Var> outputs, ExecTarget mode, TaskProps props) {
    props.assertInternalTypesValid();
    currBlock().addInstruction(
          FunctionCall.createFunctionCall(id, outputs, inputs, mode, props,
                                          program.foreignFunctions()));
  }

  public void execExternal(Arg cmd, List<Arg> args, List<Arg> inFiles,
                          List<Var> outFiles, Redirects<Arg> redirects,
                          boolean hasSideEffects, boolean deterministic) {
    assert(Types.isStringVal(cmd));

    for (Var o: outFiles) {
      assert(Types.isFileVal(o) || Types.isVoidVal(o));
    }

    for (Arg i: inFiles) {
      assert(Types.isFileVal(i));
    }


    currBlock().addInstruction(new ExecExternal(cmd, inFiles, outFiles,
                      args, redirects, hasSideEffects, deterministic));
  }

  public void initLocalOutFile(Var localOutFile, Arg fileName,
                                                 Var fileFuture) {
    Var isMapped = WrapUtil.getIsMapped(currBlock(),
          currBlock().statementEndIterator(), fileFuture);
    currBlock().addInstruction(TurbineOp.initLocalOutFile(
                            localOutFile, fileName, isMapped.asArg()));
  }

  public void arrayRetrieve(Var dst, Var arrayVar, Arg arrIx) {
    currBlock().addInstruction(
        TurbineOp.arrayRetrieve(dst, arrayVar, arrIx));
  }

  /**
   * Create alias to array member
   * @param dst
   * @param arrayVar
   * @param arrIx
   */
  public void arrayCreateAlias(Var dst, Var arrayVar, Arg arrIx) {
    currBlock().addInstruction(
        TurbineOp.arrayCreateAlias(dst, arrayVar, arrIx));
  }

  public void arrayCopyOutImm(Var dst, Var arrayVar, Arg arrIx) {
    currBlock().addInstruction(
          TurbineOp.arrayCopyOutImm(dst, arrayVar, arrIx));
  }


  public void arrayCopyOutFuture(Var dst, Var arrayVar, Var indexVar) {
    currBlock().addInstruction(
          TurbineOp.arrayCopyOutFuture(dst, arrayVar, indexVar));
  }

  public void arrayRefCopyOutImm(Var dst, Var arrayVar, Arg arrIx) {
    currBlock().addInstruction(
        TurbineOp.arrayRefCopyOutImm(dst, arrayVar, arrIx));
  }

  public void arrayRefCopyOutFuture(Var dst, Var arrayVar,
                                     Var indexVar) {
    currBlock().addInstruction(
          TurbineOp.arrayRefCopyOutFuture(dst, arrayVar, indexVar));
  }

  public void arrayStore(Var array, Arg ix, Arg member) {
    currBlock().addInstruction(TurbineOp.arrayStore(array, ix, member));
  }

  public void arrayStoreFuture(Var array, Var ixVar, Arg member) {
    currBlock().addInstruction(
        TurbineOp.arrayStoreFuture(array, ixVar, member));
  }

  public void arrayRefStoreImm(Var array, Arg ix, Arg member) {
    currBlock().addInstruction(TurbineOp.arrayRefStoreImm(array, ix, member));
  }

  public void arrayRefStoreFuture(Var array, Var ixVar, Arg member) {
    currBlock().addInstruction(
            TurbineOp.arrayRefStoreFuture(array, ixVar, member));
  }

  public void arrayCopyInImm(Var array, Arg ix, Var member) {
    currBlock().addInstruction(
        TurbineOp.arrayCopyInImm(array, ix, member));
  }

  public void arrayCopyInFuture(Var array, Var ix, Var member) {
    currBlock().addInstruction(
        TurbineOp.arrayCopyInFuture(array, ix, member));
  }

  public void arrayRefCopyInImm(Var array, Arg ix, Var member) {
    currBlock().addInstruction(
        TurbineOp.arrayRefCopyInImm(array, ix, member));
  }

  public void arrayRefCopyInFuture(Var array, Var ix, Var member) {
    currBlock().addInstruction(
        TurbineOp.arrayRefCopyInFuture(array, ix, member));
  }

  public void arrayBuild(Var array, List<Arg> keys, List<Var> vals) {
    currBlock().addInstruction(
        TurbineOp.arrayBuild(array, keys, Arg.fromVarList(vals)));
  }

  public void arrayCreateNestedFuture(Var result,
      Var array, Var ix) {
    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedFuture(result, array, ix));
  }

  public void arrayCreateNestedImm(Var result,
      Var arrayVar, Arg arrIx) {
    currBlock().addInstruction(
      TurbineOp.arrayCreateNestedImm(result,
          arrayVar, arrIx));
  }

  public void arrayRefCreateNestedImm(Var result,
                                      Var array, Arg ix) {
    currBlock().addInstruction(
      TurbineOp.arrayRefCreateNestedImmIx(result, array, ix));
  }

  public void arrayRefCreateNestedFuture(Var result, Var array, Var ix) {
    currBlock().addInstruction(
        TurbineOp.arrayRefCreateNestedComputed(result, array, ix));
  }

  public void asyncCopy(Var dst, Var src) {
    currBlock().addInstruction(TurbineOp.asyncCopy(dst, src));
  }

  public void syncCopy(Var dst, Var src) {
    currBlock().addInstruction(TurbineOp.syncCopy(dst, src));
  }

  public void bagInsert(Var bag, Arg elem) {
    currBlock().addInstruction(TurbineOp.bagInsert(bag, elem, Arg.ZERO));
  }

  public void assignRef(Var target, Var src, long readRefs, long writeRefs) {
    currBlock().addInstruction(
        TurbineOp.storeRef(target, src, readRefs, writeRefs));
  }

  public void derefScalar(Var dst, Var src) {
    assert(Types.isScalarFuture(dst));
    assert(Types.isRef(src));
    assert(src.type().memberType().assignableTo(dst.type()));
    currBlock().addInstruction(
        TurbineOp.derefScalar(dst, src));
  }

  public void derefFile(Var target, Var src) {
    assert(Types.isFile(target));
    assert(Types.isFileRef(src));
    currBlock().addInstruction(
        TurbineOp.derefFile(target, src));
  }

  public void retrieveRef(Var target, Var src, boolean mutable) {
    currBlock().addInstruction(
        TurbineOp.retrieveRef(target, src, mutable));
  }

  public void makeAlias(Var dst, Var src) {
    assert(src.type().equals(dst.type()));
    assert(dst.storage() == Alloc.ALIAS);
    currBlock().addInstruction(
        TurbineOp.copyRef(dst, src));
  }

  public void retrieveScalar(Var dst, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveScalar(dst, src));
  }

  public void assignScalar(Var dst, Arg src) {
    currBlock().addInstruction(TurbineOp.assignScalar(dst, src));
  }

  public void freeBlob(Var blobVal) {
    assert(Types.isBlobVal(blobVal));
    currBlock().addCleanup(blobVal, TurbineOp.freeBlob(blobVal));
  }

  public void isMapped(Var dst, Var file) {
    currBlock().addInstruction(TurbineOp.isMapped(dst, file));
  }

  public void assignFile(Var target, Arg src, Arg setName) {
    currBlock().addInstruction(TurbineOp.assignFile(target, src, setName));
  }

  public void retrieveFile(Var target, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveFile(target, src));
  }

  public void copyFile(Var target, Var src) {
    assert(Types.isFile(src));
    assert(Types.isFile(target));
    // Generate different code depending on whether target is mapped,
    // and depending on whether it is known at compile-time or not
    Ternary targetMapped = target.isMapped();
    Block block = currBlock();
    if (targetMapped == Ternary.TRUE) {
      copyFile(block, target, src, true, Arg.TRUE);
    } else if (targetMapped == Ternary.FALSE) {
      copyFile(block, target, src, false, Arg.FALSE);
    } else {
      assert(targetMapped == Ternary.MAYBE);
      Var targetMappedV = block.declareUnmapped(Types.V_BOOL,
          block.uniqueVarName(Var.OPT_VAR_PREFIX + "mapped_" + target.name()),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, VarProvenance.optimizerTmp());
      block.addInstruction(TurbineOp.isMapped(targetMappedV, target));
      IfStatement ifMapped = new IfStatement(targetMappedV.asArg());
      block.addStatement(ifMapped);
      copyFile(ifMapped.thenBlock(), target, src, true, targetMappedV.asArg());
      copyFile(ifMapped.elseBlock(), target, src, false, targetMappedV.asArg());
    }
  }

  /**
   * Generate code to copy a file
   * @param block
   * @param target
   * @param src
   * @param compileForTargetMapped whether to compile code for the target
   *          being mapped or not
   * @param targetMapped runtime value of mapping
   */
  private void copyFile(Block block, Var target, Var src,
        boolean compileForTargetMapped, Arg targetMapped) {
    assert(Types.isFile(target));
    assert(Types.isFile(src));
    assert(src.type().assignableTo(target.type()));
    assert(!compileForTargetMapped ||
        target.type().fileKind().supportsPhysicalCopy());
    assert(Types.isBoolVal(targetMapped));

    Var targetFilename = null;
    List<WaitVar> waitVars;
    if (compileForTargetMapped) {
      // Wait for target filename and src file
      targetFilename = block.declareUnmapped(Types.F_STRING,
          OptUtil.optFilenamePrefix(block, target),
          Alloc.ALIAS, DefType.LOCAL_COMPILER, VarProvenance.filenameOf(target));

      block.addInstruction(TurbineOp.getFileNameAlias(targetFilename, target));

      waitVars = Arrays.asList(new WaitVar(src, false),
                       new WaitVar(targetFilename, false));
    } else {
      // Don't need target filename, just wait for src file
      waitVars = Arrays.asList(new WaitVar(src, false));
    }

    WaitMode waitMode;
    ExecTarget taskMode;
    if (compileForTargetMapped) {
      // Do physical copy on worker
      waitMode = WaitMode.TASK_DISPATCH;
      taskMode = ExecTarget.dispatched(ExecContext.defaultWorker());
    } else {
      waitMode = WaitMode.WAIT_ONLY;
      taskMode = ExecTarget.nonDispatchedAny();
    }

    WaitStatement wait = new WaitStatement(
        currFunction.id() + ":wait:" + src.name(), waitVars,
        PassedVar.NONE, Var.NONE,
        waitMode, false, taskMode, new TaskProps());
    block.addContinuation(wait);

    Block waitBlock = wait.getBlock();

    // Retrieve src file info
    Var srcVal = waitBlock.declareUnmapped(Types.retrievedType(src),
        OptUtil.optVPrefix(waitBlock, src), Alloc.LOCAL,
        DefType.LOCAL_COMPILER, VarProvenance.valueOf(src));
    waitBlock.addInstruction(TurbineOp.retrieveFile(srcVal, src));

    // Assign filename if unmapped
    Var assignFilename = waitBlock.declareUnmapped(Types.V_BOOL,
            waitBlock.uniqueVarName(Var.OPT_VAR_PREFIX + "assignfile"),
            Alloc.LOCAL, DefType.LOCAL_COMPILER,
            VarProvenance.unknown());
    waitBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.NOT,
                                    assignFilename, targetMapped));

    if (compileForTargetMapped) {
      Var targetFilenameVal = waitBlock.declareUnmapped(Types.V_STRING,
          OptUtil.optVPrefix(waitBlock, targetFilename), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, VarProvenance.valueOf(targetFilename));
      Var targetVal = waitBlock.declareUnmapped(Types.retrievedType(target),
          OptUtil.optVPrefix(waitBlock, target), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, VarProvenance.valueOf(target));

      // Setup local targetfile
      waitBlock.addInstruction(TurbineOp.retrieveScalar(
              targetFilenameVal, targetFilename));
      waitBlock.addInstruction(TurbineOp.initLocalOutFile(
              targetVal, targetFilenameVal.asArg(), Arg.TRUE));

      // Actually do the copy of file contents
      waitBlock.addInstruction(TurbineOp.copyFileContents(targetVal, srcVal));

      // Set target.  Since mapped, will not set target filename
      // Provide targetMapped arg to avoid confusing optimiser
      waitBlock.addInstruction(TurbineOp.assignFile(
              target, targetVal.asArg(), assignFilename.asArg()));
    } else {
      // Set target.  Since unmapped, will set target filename
      // Provide targetMapped arg to avoid confusing optimiser
      waitBlock.addInstruction(TurbineOp.assignFile(
              target, srcVal.asArg(), assignFilename.asArg()));
    }
  }

  public void assignArray(Var target, Arg src) {
    currBlock().addInstruction(TurbineOp.assignArray(target, src));
  }

  public void retrieveArray(Var target, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveArray(target, src));
  }

  public void assignBag(Var target, Arg src) {
    currBlock().addInstruction(TurbineOp.assignBag(target, src));
  }

  public void retrieveBag(Var target, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveBag(target, src));
  }

  public void assignStruct(Var target, Arg src) {
    currBlock().addInstruction(TurbineOp.assignStruct(target, src));
  }

  public void retrieveStruct(Var target, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveStruct(target, src));
  }

  public void storeRecursive(Var target, Arg src) {
    currBlock().addInstruction(TurbineOp.storeRecursive(target, src));
  }

  public void retrieveRecursive(Var target, Var src) {
    currBlock().addInstruction(TurbineOp.retrieveRecursive(target, src));
  }

  public void decrLocalFileRef(Var fileVal) {
    assert(Types.isFileVal(fileVal));
    currBlock().addCleanup(fileVal, TurbineOp.decrLocalFileRef(fileVal));
  }

  public void intrinsicCall(IntrinsicFunction intF, List<Var> iList,
      List<Var> oList, TaskProps props) {
    Block block = currBlock();

    switch (intF) {
      case FILENAME: {
        assert(iList.size() == 1) : "Wrong # input args for filename";
        assert(oList.size() == 1) : "Wrong # output args for filename";
        Var filename = oList.get(0);
        Var file = iList.get(0);
        assert(Types.isString(filename)) : "Wrong output type for filename";
        assert(Types.isFile(file)) : "Wrong input type for filename";;
        // Implement as alias lookup, then copy
        String filenameAliasN = OptUtil.optFilenamePrefix(block, file);
        Var filenameAlias = block.declareUnmapped(Types.F_STRING,
            filenameAliasN, Alloc.ALIAS, DefType.LOCAL_COMPILER,
            VarProvenance.filenameOf(file));
        block.addInstruction(TurbineOp.getFileNameAlias(filenameAlias, file));
        block.addInstruction(Builtin.createAsync(BuiltinOpcode.COPY_STRING,
                                  filename, filenameAlias.asArg().asList()));
        break;
      }
      default:
        throw new STCRuntimeError("Intrinsic " + intF +
                                  " unknown to middle end");
    }
  }

  /**
   * All default task properties
   * @param op
   * @param out
   * @param in
   */
  public void localOp(BuiltinOpcode op, Var out, List<Arg> in) {
    currBlock().addInstruction(Builtin.createLocal(op, out, in));
  }

  /**
   * All default task properties
   * @param op
   * @param out
   * @param in
   */
  public void asyncOp(BuiltinOpcode op, Var out, List<Arg> in) {
    asyncOp(op, out, in, new TaskProps());
  }

  public void asyncOp(BuiltinOpcode op, Var out,
                                    List<Arg> in, TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();

    if (out != null) {
      assert(Types.isPrimFuture(out));
    }
    currBlock().addInstruction(Builtin.createAsync(op, out, in, props));
  }

  public void unpackArrayToFlat(Var flatLocalArray, Arg inputArray) {
    // TODO: other container types?
    assert(Types.isArray(inputArray));
    NestedContainerInfo c = new NestedContainerInfo(inputArray.type());
    assert(Types.isArrayLocal(flatLocalArray));
    Type baseType = c.baseType;

    // Get type inside reference
    if (Types.isRef(baseType)) {
      baseType = Types.retrievedType(baseType);
    }

    Type memberValT = Types.retrievedType(baseType);

    assert(memberValT.assignableTo(Types.containerElemType(flatLocalArray)))
      : memberValT + " " + flatLocalArray;

    currBlock().addInstruction(
        TurbineOp.unpackArrayToFlat(flatLocalArray, inputArray));
  }

  public void structCreateAlias(Var fieldAlias, Var struct,
                                List<String> fieldPath) {
    currBlock().addInstruction(
        TurbineOp.structCreateAlias(fieldAlias, struct, fieldPath));
  }

  public void structRetrieveSub(Var target, Var struct,
      List<String> fieldPath) {
    currBlock().addInstruction(
        TurbineOp.structRetrieveSub(target, struct, fieldPath, Arg.ZERO));
  }

  public void structCopyOut(Var target, Var struct,
                            List<String> fieldPath) {
    currBlock().addInstruction(
        TurbineOp.structCopyOut(target, struct, fieldPath));
  }

  public void structRefCopyOut(Var target, Var struct,
                                List<String> fieldPath) {
    currBlock().addInstruction(
        TurbineOp.structRefCopyOut(target, struct, fieldPath));
  }

  public void structStoreSub(Var struct, List<String> fieldPath,
                          Arg fieldVal) {
    currBlock().addInstruction(TurbineOp.structStoreSub(struct,
                                fieldPath, fieldVal));
  }

  public void structCopyIn(Var struct, List<String> fieldPath,
                            Var fieldVar) {
    currBlock().addInstruction(TurbineOp.structCopyIn(struct,
                                fieldPath, fieldVar));
  }

  public void structRefCopyIn(Var struct, List<String> fieldPath,
                              Var fieldVar) {
    currBlock().addInstruction(TurbineOp.structRefCopyIn(struct,
                                          fieldPath, fieldVar));
  }

  public void structCreateNested(Var result, Var struct, List<String> fields) {
    currBlock().addInstruction(
      TurbineOp.structCreateNested(result, struct, fields));
  }

  public void addGlobalConst(Var var, Arg val) {
    assert(val.isConst());
    program.constants().add(var, val);
  }

  public void initScalarUpdateable(Var updateable, Arg val) {
    assert(Types.isPrimUpdateable(updateable));
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type() + " not yet supported");
    }
    assert(val.isImmFloat());

    currBlock().addInstruction(TurbineOp.initUpdateableFloat(updateable, val));
  }

  public void latestValue(Var result, Var updateable) {
    assert(Types.isPrimUpdateable(updateable));
    assert(Types.isPrimValue(result));
    assert(updateable.type().primType() == result.type().primType());
    currBlock().addInstruction(
          TurbineOp.latestValue(result, updateable));
  }

  public void updateScalarFuture(Var updateable, Operators.UpdateMode updateMode, Var val) {
    assert(Types.isPrimUpdateable(updateable));
    assert(Types.isPrimFuture(val));
    assert(updateable.type().primType() == val.type().primType());
    assert(updateMode != null);

    currBlock().addInstruction(
          TurbineOp.update(updateable, updateMode, val));
  }

  public void updateScalarImm(Var updateable, Operators.UpdateMode updateMode,
                                                Arg val) {
    assert(Types.isPrimUpdateable(updateable));
    if (updateable.type().equals(Types.UP_FLOAT)) {
      assert(val.isImmFloat());
    } else {
      throw new STCRuntimeError("only updateable floats are"
          + " implemented so far");
    }
    assert(updateMode != null);

    currBlock().addInstruction(
          TurbineOp.updateImm(updateable, updateMode, val));
  }

  public void getFileNameAlias(Var filename, Var file,
                          boolean initUnmapped) {
    assert(Types.isString(filename));
    assert(filename.storage() == Alloc.ALIAS);
    assert(Types.isFile(file));
    if (initUnmapped) {
      WrapUtil.initOrGetFileName(currBlock(),
              currBlock().statementEndIterator(), filename, file,
              initUnmapped);
    } else {
      // Don't allow initialization of filename
      currBlock().addInstruction(TurbineOp.getFileNameAlias(filename, file));
    }
  }

  public void setFilenameVal(Var file, Arg filenameVal) {
    currBlock().addInstruction(
            TurbineOp.setFilenameVal(file, filenameVal));
  }

  public void copyInFilename(Var var, Var mapping) {
    assert(Types.isFile(var));
    assert(Types.isString(mapping));
    currBlock().addInstruction(
            TurbineOp.copyInFilename(var, mapping));
  }

  public void generateWrappedBuiltin(FnID wrapperID,
      FnID builtinID, FunctionType ft,
      List<Var> outArgs, List<Var> userInArgs, ExecTarget mode,
      boolean isParallel, boolean isTargetable)
          throws UserException {

    // TODO: add arg for parallelism and target
    // Need to pass in additional args for e.g. parallelism
    List<Var> realInArgs = new ArrayList<Var>();
    realInArgs.addAll(userInArgs);


    TaskProps props = new TaskProps();
    if (isParallel) {
      // declare compiler arg for parallelism
      Var par = new Var(Types.V_INT, Var.VALUEOF_VAR_PREFIX + "par",
                        Alloc.LOCAL, DefType.INARG, VarProvenance.optimizerTmp());
      realInArgs.add(par);
      props.put(TaskPropKey.PARALLELISM, par.asArg());

      // TODO: handle passing in parallelism
      // TODO: handle different output var setting convention
      throw new STCRuntimeError("Don't support generating wrappers " +
          "for parallel functions yet");
    }

    if (isTargetable) {
      // declare compiler arg for target
      Var locRank = new Var(Types.V_INT, Var.VALUEOF_VAR_PREFIX + "loc_rank",
          Alloc.LOCAL, DefType.INARG, VarProvenance.optimizerTmp());
      realInArgs.add(locRank);
      props.put(TaskPropKey.LOC_RANK, locRank.asArg());

      Var locStrictness = new Var(Types.V_LOC_STRICTNESS,
          Var.VALUEOF_VAR_PREFIX + "loc_strictness",
          Alloc.LOCAL, DefType.INARG, VarProvenance.optimizerTmp());
      realInArgs.add(locStrictness);
      props.put(TaskPropKey.LOC_STRICTNESS, locStrictness.asArg());

      Var locAccuracy = new Var(Types.V_LOC_ACCURACY,
          Var.VALUEOF_VAR_PREFIX + "loc_accuracy",
          Alloc.LOCAL, DefType.INARG, VarProvenance.optimizerTmp());
      realInArgs.add(locAccuracy);
      props.put(TaskPropKey.LOC_ACCURACY, locAccuracy.asArg());
    }

    Function fn = new Function(wrapperID, realInArgs, outArgs,
                               ExecTarget.syncControl());
    this.program.addFunction(fn);

    WaitMode waitMode;
    if (!mode.isDispatched() && !isParallel) {
      // Cases where function can execute on any node
      waitMode = WaitMode.WAIT_ONLY;
    } else {
      // Cases where we may need to send task to another class of worker
      waitMode = WaitMode.TASK_DISPATCH;
    }

    Block mainBlock = fn.mainBlock();

    // Check if we need to initialize mappings of output files
    boolean mustMapOutFiles =
        !program.foreignFunctions().canInitOutputMapping(builtinID);

    Pair<List<WaitVar>, Map<Var, Var>> p;
    p = WrapUtil.buildWaitVars(mainBlock, mainBlock.statementIterator(),
                         userInArgs, Var.NONE, outArgs, mustMapOutFiles);

    // Variables we must wait for
    List<WaitVar> waitVars = p.val1;

    // Track filenames corresponding to inputs
    Map<Var, Var> filenameVars = p.val2;


    WaitStatement wait = new WaitStatement(wrapperID.uniqueName() + "-argwait",
                  waitVars, PassedVar.NONE, Var.NONE,
                  waitMode, true, mode, props);

    mainBlock.addContinuation(wait);

    Block waitBlock = wait.getBlock();

    // List of instructions to go inside wait
    List<Statement> instBuffer = new ArrayList<Statement>();
    List<Arg> inVals = WrapUtil.fetchLocalOpInputs(waitBlock, userInArgs,
                                                  instBuffer, false);

    List<Var> outVals = WrapUtil.createLocalOpOutputs(waitBlock, outArgs,
                            filenameVars, instBuffer, false, mustMapOutFiles,
                            true);
    instBuffer.add(new LocalFunctionCall(builtinID, inVals, outVals,
                                         program.foreignFunctions()));

    WrapUtil.setLocalOpOutputs(waitBlock, outArgs, outVals, instBuffer,
                               !mustMapOutFiles, true);

    waitBlock.addStatements(instBuffer);
  }

  /**
   * Should be called if checkpointing is required so that it can be
   * correctly initialized.
   */
  public void requireCheckpointing() {
    program.requireCheckpointing();
  }

  public void checkpointWriteEnabled(Var v) {
    assert(Types.isBoolVal(v));
    currBlock().addInstruction(
            TurbineOp.checkpointWriteEnabled(v));
  }

  public void checkpointLookupEnabled(Var v) {
    assert(Types.isBoolVal(v));
    currBlock().addInstruction(
            TurbineOp.checkpointLookupEnabled(v));
  }


  public void writeCheckpoint(Arg key, Arg val) {
    assert(Types.isBlobVal(key));
    assert(Types.isBlobVal(val));
    currBlock().addInstruction(TurbineOp.writeCheckpoint(key, val));
  }

  public void lookupCheckpoint(Var checkpointExists, Var value,
                               Arg key) {
    assert(Types.isBlobVal(key));
    currBlock().addInstruction(
        TurbineOp.lookupCheckpoint(checkpointExists, value, key));
  }

  public void packValues(Var packedValues, List<Arg> values) {
    assert(Types.isBlobVal(packedValues));
    currBlock().addInstruction(
        TurbineOp.packValues(packedValues, values));
  }

  public void unpackValues(List<Var> values, Var packedValues) {
    assert(Types.isBlobVal(packedValues));
    currBlock().addInstruction(
        TurbineOp.unpackValues(values, packedValues.asArg()));
  }

}
