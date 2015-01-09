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
package exm.stc.ic.opt;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.InitType;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmVar;
import exm.stc.ic.tree.ICInstructions.Instruction.VarCreator;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;

public class OptUtil {

  /**
   * Generate optimiser variable name guaranteed to be unique
   * @param v
   * @return
   */
  public static String optVPrefix(Block b, Var v) {
     return optVPrefix(b, v.name());
  }

  public static String optVPrefix(Block b, String name) {
    return b.uniqueVarName(Var.joinPrefix(Var.VALUEOF_VAR_PREFIX, name));
  }

  public static String optFilenamePrefix(Block b, Var v) {
    return optFilenamePrefix(b, v.name());
  }

  public static String optFilenamePrefix(Block b, String name) {
    return b.uniqueVarName(Var.joinPrefix(Var.OPT_FILENAME_PREFIX, name));
  }

  /**
   * Build wait var list
   * @param block
   * @param it
   * @param in
   * @param out
   * @param filenameMap
   * @param waitVars the variable, and whether it must be waited
   *                  for recursively
   */
  public static void buildWaitVars(Block block, ListIterator<Statement> it,
            List<MakeImmVar> inputs, List<MakeImmVar> outputs,
            Map<Var, Var> filenameMap, List<Pair<Var, Ternary>> waitVars) {

    for (MakeImmVar in: inputs) {
      if (WrapUtil.inputMustWait(in.var)) {
        waitVars.add(makeWaitVarTernary(in.var, in.recursive));
      }
    }

    for (MakeImmVar out: outputs) {
      Var toWaitVar = WrapUtil.getWaitOutputMapping(block, it,
        out.preinitOutputMapping, filenameMap, out.var);
      if (toWaitVar != null) {
        waitVars.add(makeWaitVarTernary(toWaitVar, true));
      }
    }
  }

  private static Pair<Var, Ternary> makeWaitVarTernary(Var var, boolean recursive) {
    Pair<Var, Ternary> recCloseVar;
    if (!RefCounting.recursiveClosePossible(var)) {
      recCloseVar = Pair.create(var, Ternary.MAYBE);
    } else {
      recCloseVar = Pair.create(var, Ternary.fromBool(recursive));
    }
    return recCloseVar;
  }

  /**
   * Same as fetchValue of, but more times
   * @param block
   * @param instBuffer
   * @param vars
   * @return
   */
  public static List<Arg> fetchValuesOf(Block block, List<Instruction> instBuffer,
          List<Var> vars, boolean recursive, boolean acquireWrite) {
    List<Arg> inVals = new ArrayList<Arg>(vars.size());

    for (Var v: vars) {
      String name = optVPrefix(block, v);
      Var valueV = WrapUtil.fetchValueOf(block, instBuffer, v, name,
                                         recursive, acquireWrite);
      Arg value = Arg.newVar(valueV);
      inVals.add(value);
    }
    return inVals;
  }

  public static List<Arg> fetchValuesOf(Block block, List<Var> vars,
                                        boolean recursive, boolean acquireWrite) {
    List<Instruction> instBuffer = new ArrayList<Instruction>();
    List<Arg> vals = fetchValuesOf(block, instBuffer, vars, recursive, acquireWrite);
    block.addInstructions(instBuffer);
    return vals;
  }


  /**
   * Do the manipulation necessary to allow an old instruction
   * output variable to be replaced with a new one. Assume that
   * newOut is a value type of oldOut
   * @param function
   * @param srcBlock source block for instruction
   * @param targetBlock target block for instruction
   * @param instBuffer append any fixup instructions here
   * @param newOut
   * @param oldOut
   * @param recursive if it's to be fetched recursively
   */
  public static void replaceInstOutput(String function, Block srcBlock,
          Block targetBlock, List<Statement> instBuffer, Var newOut, Var oldOut,
          boolean initialisesOutput) {
    boolean isDerefResult =
        Types.retrievedType(oldOut).assignableTo(newOut.type());
    if (isDerefResult) {
      Var oldOutReplacement;
      if (oldOut.storage() == Alloc.ALIAS  &&
          initialisesOutput) {
        // Will need to initialise variable in this scope as before we
        // were relying on instruction to initialise it
        oldOutReplacement = new Var(oldOut.type(),
            oldOut.name(), Alloc.TEMP,
            oldOut.defType(), oldOut.provenance(), oldOut.mappedDecl());

        // Replace variable in block and in buffered instructions
        replaceVarDeclaration(srcBlock, oldOut, oldOutReplacement);

        Map<Var, Arg> renames = Collections.singletonMap(
                                oldOut, Arg.newVar(oldOutReplacement));
        for (Statement inst: instBuffer) {
          inst.renameVars(function, renames, RenameMode.REPLACE_VAR);
        }
      } else {
        oldOutReplacement = oldOut;
      }

      WrapUtil.assignOutput(targetBlock, instBuffer,
                false, oldOutReplacement, newOut,
                false);
    } else {
      throw new STCRuntimeError("Tried to replace instruction"
          + " output var " + oldOut + " with " + newOut + ": this doesn't make sense"
          + " to optimizer");
    }
  }

  /**
   * Replace variable declaration in block or one of parents
   * @param block
   * @param oldOut
   * @param refVar
   */
  private static void replaceVarDeclaration(Block block, Var oldVar,
      Var newVar) {
    assert(oldVar.type().equals(newVar.type()));
    assert(oldVar.name().equals(newVar.name()));
    Block curr = block;
    while (true) {
      if (curr.replaceVarDeclaration(block.getFunction().getName(),
                                     oldVar, newVar)) {
        // Success
        return;
      }
      if (curr.getType() == BlockType.MAIN_BLOCK) {
        throw new STCRuntimeError("Could not find definition of " + oldVar);
      } else {
        Continuation cont = block.getParentCont();
        assert(!cont.constructDefinedVars().contains(oldVar)) :
            "can't replace construct defined vars";
        curr = cont.parent();
      }
    }
  }

  /**
   * Declare and fetch inputs for conversion to local operation
   * @param block
   * @param inputs
   * @param instBuffer
   * @return
   */
  public static List<Arg> fetchMakeImmInputs(Block block, List<MakeImmVar> inputs,
      List<Statement> instBuffer) {
    if (inputs == null) {
      // Gracefully handle null as empty list
      inputs = MakeImmVar.NONE;
    }
    List<Arg> inVals = new ArrayList<Arg>(inputs.size());
    for (MakeImmVar inArg: inputs) {
      if (inArg.fetch) {
        String name = optVPrefix(block, inArg.var.name());
        inVals.add(WrapUtil.fetchValueOf(block, instBuffer,
                   inArg.var, name, inArg.recursive,
                   inArg.acquireWriteRefs).asArg());
      }
    }
    return inVals;
  }

  /**
   * Build output variable list for conversion to local operation
   * @param block
   * @param outputFutures
   * @param filenameVars
   * @param instBuffer
   * @param uniquifyNames if it isn't safe to use default name prefix,
   *      e.g. if we're in the middle of optimizations
   * @param preinitOutputMapping
   * @param store recursively
   * @return
   */
  public static List<Var> createMakeImmOutputs(Block block,
      List<MakeImmVar> outputFutures, Map<Var, Var> filenameVars,
      List<Statement> instBuffer) {
    if (outputFutures == null) {
      // Gracefully handle null as empty list
      outputFutures = MakeImmVar.NONE;
    }
    List<Var> outVals = new ArrayList<Var>();
    for (MakeImmVar outArg: outputFutures) {
      if (Types.isPrimUpdateable(outArg.var)) {
        // Use standard representation
        outVals.add(outArg.var);
      } else {
        outVals.add(WrapUtil.createLocalOutputVar(outArg.var, filenameVars,
                   block, instBuffer, true, outArg.preinitOutputMapping,
                   outArg.recursive));
      }
    }
    return outVals;
  }


  public static List<Var> createLocalOpOutputVars(Block block,
          ListIterator<Statement> insertPos,
          List<MakeImmVar> outFutures, Map<Var, Var> outputFilenames) {
    if (outFutures == null) {
      return Collections.emptyList();
    }

    List<Statement> instBuffer = new ArrayList<Statement>();

    List<Var> outValVars = new ArrayList<Var>(outFutures.size());
    for (MakeImmVar outFut: outFutures) {
      outValVars.add(WrapUtil.createLocalOutputVar(outFut.var,
           outputFilenames, block, instBuffer, true,
           outFut.preinitOutputMapping, outFut.recursive));
    }

    for (Statement stmt: instBuffer) {
      insertPos.add(stmt);
    }

    return outValVars;
  }

  public static void fixupImmChange(String function, Block srcBlock,
          Block targetBlock, Instruction oldInst,
          MakeImmChange change,
          List<Statement> instBuffer,
          List<Var> newOutVars, List<MakeImmVar> oldOutVars) {
    instBuffer.addAll(Arrays.asList(change.newInsts));

    Logger logger = Logging.getSTCLogger();
    if (logger.isTraceEnabled()) {
      logger.trace("Swapped " + oldInst + " for " +
                   Arrays.asList(change.newInsts));
    }

    if (!change.isOutVarSame()) {
      // Output variable of instruction changed, need to fix up
      Var newOut = change.newOut;
      Var oldOut = change.oldOut;
      boolean initOutput = false;
      for (Pair<Var, InitType> init: oldInst.getInitialized()) {
        if (init.val2 == InitType.FULL &&
            init.val1.equals(oldOut)) {
          initOutput = true;
          break;
        }
      }

      replaceInstOutput(function, srcBlock, targetBlock, instBuffer,
                         newOut, oldOut, initOutput);
    }

    // Now copy back values into future
    if (change.storeOutputVals) {
      setLocalOutputs(targetBlock, oldOutVars, newOutVars, instBuffer);
    }
  }

  /**
   * Set futures from output values
   * @param outFuts
   * @param outVals
   * @param instBuffer
   */
  public static void setLocalOutputs(Block block,
      List<MakeImmVar> outFuts, List<Var> outVals,
      List<Statement> instBuffer) {
    if (outFuts == null) {
      assert(outVals == null || outVals.isEmpty());
      return;
    }
    assert(outVals.size() == outFuts.size());
    for (int i = 0; i < outVals.size(); i++) {
      MakeImmVar outArg = outFuts.get(i);
      Var outVal = outVals.get(i);

      if (outArg.var.equals(outVal)) {
        // Do nothing: the variable wasn't substituted
      } else {
        WrapUtil.assignOutput(block, instBuffer, !outArg.preinitOutputMapping,
                              outArg.var, outVal, outArg.recursive);
      }
    }
  }

  /**
   * Union of Instruction and Continuation, useful in some cases
   */
  public static class InstOrCont {
    public static enum InstOrContType {
      INSTRUCTION,
      CONTINUATION,
    }
    public InstOrCont(Instruction i) {
      this.inst = i;
      this.cont = null;
      this._type = InstOrContType.INSTRUCTION;
    }
    public InstOrCont(Continuation c) {
      this.inst = null;
      this.cont = c;
      this._type = InstOrContType.CONTINUATION;
    }

    private final Instruction inst;
    private final Continuation cont;
    private final InstOrContType _type;

    public InstOrContType type() {
      return _type;
    }

    public Instruction instruction() {
      if (_type != InstOrContType.INSTRUCTION) {
        throw new STCRuntimeError("InstOrCont not an " +
                "instruction, was: " + _type);

      }
      return inst;
    }

    public Continuation continuation() {
      if (_type != InstOrContType.CONTINUATION) {
        throw new STCRuntimeError("InstOrCont not an " +
            "continuation, was: " + _type);

      }
      return cont;
    }

    @Override
    public String toString() {
      switch (_type) {
        case CONTINUATION:
          StringBuilder sb = new StringBuilder();
          this.cont.prettyPrint(sb, "     ");
          return "Continuation: " + sb.toString();
        case INSTRUCTION:
          assert(inst != null);
          return "Instruction: " + this.inst.toString();
        default:
          throw new STCRuntimeError("invalid tag " + _type);
      }
    }
  }

  public static Var fetchForLocalOp(Block block,
          List<? super Instruction> instBuffer, Var var,
          boolean recursive, boolean acquireWrite) {
    return WrapUtil.fetchValueOf(block, instBuffer, var,
                             OptUtil.optVPrefix(block, var),
                             recursive, acquireWrite);
  }

  public static class OptVarCreator implements VarCreator {
    public OptVarCreator(Block block) {
      this.block = block;
    }

    private final Block block;

    @Override
    public Var createDerefTmp(Var toDeref) {
      return OptUtil.createDerefTmp(block, toDeref);
    }

  }

  /**
   * Create dereferenced variable given a reference
   */
  public static Var createDerefTmp(Block block, Var toDeref) {
    Alloc storage;
    if (Types.isRef(toDeref)) {
      storage = Alloc.ALIAS;
    } else {
      storage = Alloc.LOCAL;
    }

    Type resType = Types.retrievedType(toDeref);
    String name = block.uniqueVarName(
        Var.joinPrefix(Var.VALUEOF_VAR_PREFIX, toDeref.name()));
    Var res = new Var(resType, name,
        storage, DefType.LOCAL_COMPILER,
        VarProvenance.valueOf(toDeref));

    block.addVariable(res);
    return res;
  }

  public static Var createTmpAlias(Block block, Var orig) {
    String name = block.uniqueVarName(
        Var.joinPrefix(Var.ALIAS_VAR_PREFIX, orig.name()));
    Var res = new Var(orig.type(), name,
        Alloc.ALIAS, DefType.LOCAL_COMPILER,
        VarProvenance.renamed(orig));

    block.addVariable(res);
    return res;
  }
}