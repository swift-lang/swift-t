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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.TurbineOp;

/**
 * Utility functions used to generate wrappers for local operations
 * @author tim
 *
 */
public class WrapUtil {

  /**
   * Fetch the value of a variable
   * @param block
   * @param instBuffer append fetch instruction to this list
   * @param var the variable to fetch the value of
   * @param acquireWrite if the var is a reference, do we acquire
   *                      write refcounts?
   * @return variable holding value
   */
  public static Var fetchValueOf(Block block,
          List<? super Instruction> instBuffer,
          Var var, String valName, boolean recursive, boolean acquireWrite) {

    Type valueT = Types.retrievedType(var, recursive);

    if (Types.isPrimUpdateable(var)) {
      Var value_v = createValueVar(valName, valueT, var);

      block.addVariable(value_v);
      instBuffer.add(TurbineOp.latestValue(value_v, var));
      return value_v;
    } else if (Types.isPrimFuture(var)) {
      // The result will be a value
      // Use the OPT_VALUE_VAR_PREFIX to make sure we don't clash with
      //  something inserted by the frontend (this caused problems before)
      Var value_v = createValueVar(valName, valueT, var);
      block.addVariable(value_v);
      instBuffer.add(ICInstructions.retrievePrim(value_v, var));

      // Add cleanup action if needed
      if (Types.isBlobVal(valueT)) {
        block.addCleanup(value_v, TurbineOp.freeBlob(value_v));
      }
      return value_v;
    } else if (Types.isRef(var)) {
      // The result will be an alias
      Var deref = new Var(valueT, valName,
          Alloc.ALIAS, DefType.LOCAL_COMPILER,
          VarProvenance.valueOf(var));
      block.addVariable(deref);
      instBuffer.add(TurbineOp.retrieveRef(deref, var, acquireWrite));
      return deref;
    } else if (Types.isContainer(var) && recursive) {
      Var deref = new Var(valueT, valName,
              Alloc.LOCAL, DefType.LOCAL_COMPILER,
              VarProvenance.valueOf(var));
      block.addVariable(deref);
      instBuffer.add(TurbineOp.retrieveRecursive(deref, var));
      return deref;
    } else if (Types.isStruct(var) && recursive) {
      Var deref = new Var(valueT, valName,
          Alloc.LOCAL, DefType.LOCAL_COMPILER,
          VarProvenance.valueOf(var));
      block.addVariable(deref);

      boolean mustUseRecursive = ((StructType)var.type().getImplType()).hasRefField();

      Instruction structRetrieve = mustUseRecursive ?
            TurbineOp.retrieveRecursive(deref, var) :
            TurbineOp.retrieveStruct(deref, var);

      instBuffer.add(structRetrieve);
      return deref;
    } else if ((Types.isContainer(var) || Types.isStruct(var))
                && !recursive) {
      Var deref = new Var(valueT, valName,
          Alloc.LOCAL, DefType.LOCAL_COMPILER,
          VarProvenance.valueOf(var));
      block.addVariable(deref);
      if (Types.isArray(var)) {
        instBuffer.add(TurbineOp.retrieveArray(deref, var));
      } else if (Types.isBag(var)) {
        instBuffer.add(TurbineOp.retrieveBag(deref, var));
      } else {
        assert(Types.isStruct(var));
        instBuffer.add(TurbineOp.retrieveStruct(deref, var));
      }
      return deref;
    } else {
      throw new STCRuntimeError("shouldn't be possible to get here");
    }
  }

  public static Var createValueVar(String name, Type type, Var orig) {
    Var value_v = new Var(type, name, Alloc.LOCAL, DefType.LOCAL_COMPILER,
                          VarProvenance.valueOf(orig));
    return value_v;
  }

  /**
   * Work out which inputs/outputs need to be waited for before
   * executing a statement depending on them
   * @param block
   * @param instInsertIt position to insert instructions
   * @param inArgs
   * @param outArgs
   * @param mustMapOutFiles
   * @return (a list of variables to wait for,
   *          the mapping from file output vars to filenames)
   */
  public static Pair<List<WaitVar>, Map<Var, Var>> buildWaitVars(
      Block block, ListIterator<Statement> instInsertIt,
      List<Var> inArgs, List<Var> otherWaitArgs, List<Var> outArgs,
      boolean mustMapOutFiles) {

    List<WaitVar> waitVars = new ArrayList<WaitVar>(inArgs.size());
    Map<Var, Var> filenameVars = new HashMap<Var, Var>();

    for (List<Var> waitArgList: Arrays.asList(inArgs, otherWaitArgs)) {
      for (Var in: waitArgList) {
        if (inputMustWait(in)) {
          waitVars.add(new WaitVar(in, false));
        }
      }
    }

    for (Var out: outArgs) {
      Var waitMapping = getWaitOutputMapping(block, instInsertIt,
                          mustMapOutFiles, filenameVars, out);
      if (waitMapping != null) {
        waitVars.add(new WaitVar(waitMapping, false));
      }
    }
    return Pair.create(waitVars, filenameVars);
  }

  /**
   *
   * @param block
   * @param instInsertIt
   * @param mustMapOutFiles
   * @param waitVars
   * @param filenameVars updated with any filenames
   * @param out
   * @return wait var if must wait, null otherwise
   */
  public static Var getWaitOutputMapping(Block block,
      ListIterator<Statement> instInsertIt, boolean mustMapOutFiles,
      Map<Var, Var> filenameVars, Var out) {
    if (Types.isFile(out)) {
      // Must wait on filename of output var
      String name = block.uniqueVarName(Var.WRAP_FILENAME_PREFIX +
                                        out.name());
      Var filenameTmp = block.declareUnmapped(Types.F_STRING,
          name, Alloc.ALIAS, DefType.LOCAL_COMPILER,
          VarProvenance.filenameOf(out));
      boolean initIfUnmapped = mustMapOutFiles;
      Var filenameWaitVar =
           initOrGetFileName(block, instInsertIt, filenameTmp, out, initIfUnmapped);

      filenameVars.put(out, filenameTmp);

      return filenameWaitVar;
    }
    return null;
  }

  public static boolean inputMustWait(Var in) {
    return !Types.isPrimUpdateable(in) && in.storage() != Alloc.GLOBAL_CONST;
  }

  /**
   * Get the filename for a file, initializing it to a temporary value in
   * the case where it's not mapped.
   * @param block
   * @param filename alias to be initialized
   * @param file
   * @param initIfUnmapped
   * @return variable to wait on for filename
   */
  public static Var initOrGetFileName(Block block,
            ListIterator<Statement> insertPos, Var filename, Var file,
            boolean initIfUnmapped) {
    assert(Types.isString(filename));
    assert(filename.storage() == Alloc.ALIAS);
    assert(Types.isFile(file.type()));
    Instruction getFileName = TurbineOp.getFileNameAlias(filename, file);

    if (file.isMapped() == Ternary.TRUE ||
        !file.type().fileKind().supportsTmpImmediate()) {
      // Just get the mapping in these cases:
      // - File is definitely mapped
      // - The file type doesn't support temporary creation
      insertPos.add(getFileName);
      return filename;
    } else {
      Var waitVar = null;
      if (!initIfUnmapped) {
        waitVar = block.declareUnmapped(Types.F_STRING,
            OptUtil.optVPrefix(block, file.name() + "filename-wait"),
            Alloc.ALIAS, DefType.LOCAL_COMPILER,
            VarProvenance.filenameOf(file));
      }

      // Use optimizer var prefixes to avoid clash with any frontend vars
      Var isMapped = getIsMapped(block, insertPos, file);

      IfStatement ifMapped = new IfStatement(isMapped.asArg());
      ifMapped.setParent(block);

      if (!initIfUnmapped) {

        // Wait on filename
        insertPos.add(getFileName);
        ifMapped.thenBlock().addStatement(
            TurbineOp.copyRef(waitVar, filename));
      }

      // Case when not mapped: init with tmp
      Block elseB = ifMapped.elseBlock();

      if (initIfUnmapped) {
        Var filenameVal = elseB.declareUnmapped(Types.V_STRING,
            OptUtil.optFilenamePrefix(elseB, file), Alloc.LOCAL,
            DefType.LOCAL_COMPILER, VarProvenance.filenameOf(file));
        initTemporaryFileName(elseB.statementEndIterator(), file, filenameVal);
        // Get the filename again but can assume mapping initialized
        insertPos.add(getFileName);
      } else {
        // Dummy wait variable
        elseB.addStatement(TurbineOp.copyRef(waitVar,
                              Var.NO_WAIT_STRING_VAR));
      }

      insertPos.add(ifMapped);

      if (initIfUnmapped) {
        return filename;
      } else {
        return waitVar;
      }
    }
  }

  public static Var getIsMapped(Block block,
      ListIterator<? super Instruction> insertPos, Var file) {
    Var isMapped = block.declareUnmapped(Types.V_BOOL,
          OptUtil.optVPrefix(block, "mapped_" + file.name()), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, VarProvenance.optimizerTmp());

    insertPos.add(TurbineOp.isMapped(isMapped, file));
    return isMapped;
  }

  /**
   * Initialize a file variable with a mapping to a temp file
   * @param insertPos place to insert instructions
   * @param file this is the file variable to be mapped
   * @param filenameVal this is filename
   */
  public static void initTemporaryFileName(
      ListIterator<? super Statement> insertPos, Var file, Var filenameVal) {
    assert(Types.isFile(file));
    assert(Types.isStringVal(filenameVal));
    assert(file.type().fileKind().supportsTmpImmediate()) :
        "Can't create temporary file for type " + file.type();
    // Select temporary file name
    insertPos.add(TurbineOp.chooseTmpFilename(filenameVal));

    // TODO: would be ideal to be able to just keep local
    // Set the filename on the file var
    insertPos.add(TurbineOp.setFilenameVal(file, filenameVal.asArg()));
  }

  /**
   * Declare and fetch inputs for local operation
   * @param block
   * @param inputs
   * @param instBuffer
   * @param uniquifyNames
   * @return
   */
  public static List<Arg> fetchLocalOpInputs(Block block, List<Var> inputs,
      List<Statement> instBuffer, boolean uniquifyNames) {
    if (inputs == null) {
      // Gracefully handle null as empty list
      inputs = Var.NONE;
    }
    List<Arg> inVals = new ArrayList<Arg>(inputs.size());
    for (Var inArg: inputs) {
      String name = valName(block, inArg, uniquifyNames);
      inVals.add(WrapUtil.fetchValueOf(block, instBuffer,
                                       inArg, name, true, false).asArg());
    }
    return inVals;
  }

  /**
   * Build output variable list for local operation
   * @param block
   * @param outputFutures
   * @param filenameVars
   * @param instBuffer
   * @param uniquifyNames if it isn't safe to use default name prefix,
   *      e.g. if we're in the middle of optimizations
   * @param mustInitOutputMapping
   * @param store recursively
   * @return
   */
  public static List<Var> createLocalOpOutputs(Block block,
      List<Var> outputFutures, Map<Var, Var> filenameVars,
      List<Statement> instBuffer, boolean uniquifyNames,
      boolean mustInitOutputMapping, boolean recursive) {
    if (outputFutures == null) {
      // Gracefully handle null as empty list
      outputFutures = Var.NONE;
    }
    List<Var> outVals = new ArrayList<Var>();
    for (Var outArg: outputFutures) {
      outVals.add(WrapUtil.createLocalOutputVar(outArg, filenameVars,
                   block, instBuffer, uniquifyNames, mustInitOutputMapping,
                   recursive));
    }
    return outVals;
  }

  /**
   * Declare a local output variable for an operation
   * @param block
   * @param var
   * @param valName
   * @return
   */
  public static Var declareLocalOutputVar(Block block, Var var,
          String valName, boolean recursive) {
    return block.declareUnmapped(Types.retrievedType(var, recursive),
        valName, Alloc.LOCAL, DefType.LOCAL_COMPILER,
        VarProvenance.valueOf(var));
  }


  /**
   * Create an output variable for an operation, and
   * perform initialization and cleanup actions
   * @param outFut
   * @param filenameVars map from file futures to filename
   *                    vals/futures if this is a file var
   * @param block
   * @param instBuffer buffer for initialization actions
   * @param uniquifyName if it isn't safe to use default name prefix,
   *      e.g. if we're in the middle of optimizations
   * @param mustInitOutputMapping whether to initialize mappings for output files
   * @param recursive if the fetch will be recursive
   * @return
   */
  public static Var createLocalOutputVar(Var outFut,
      Map<Var, Var> filenameVars,
      Block block, List<Statement> instBuffer, boolean uniquifyName,
      boolean mustInitOutputMapping, boolean recursive) {
    if (Types.isPrimUpdateable(outFut)) {
      // Use standard representation
      return outFut;
    } else {
      String outValName = valName(block, outFut, uniquifyName);
      Var outVal = WrapUtil.declareLocalOutputVar(block, outFut, outValName,
                  recursive);
      WrapUtil.initLocalOutputVar(block, filenameVars, instBuffer,
                                  outFut, outVal, mustInitOutputMapping);
      WrapUtil.cleanupLocalOutputVar(block, outFut, outVal);
      return outVal;
    }
  }

  private static String valName(Block block, Var future, boolean uniquifyName) {
    String outValName;
    if (uniquifyName) {
      outValName = OptUtil.optVPrefix(block, future.name());
    } else {
      outValName = Var.LOCAL_VALUE_VAR_PREFIX + future.name();
    }
    return outValName;
  }

  /**
   * Initialize a local output variable for an operation
   * @param block
   * @param filenameVars map of file future to filename future
   * @param instBuffer append initialize instructions to this buffer
   * @param outFut
   * @param outVal
   * @param mapOutFile
   */
  private static void initLocalOutputVar(Block block, Map<Var, Var> filenameVars,
      List<Statement> instBuffer, Var outFut, Var outVal, boolean mustInitOutputMapping) {
    if (Types.isFile(outFut)) {
      // Initialize filename in local variable
      Var isMapped = getIsMapped(block, instBuffer.listIterator(), outFut);
      Var outFilename = filenameVars.get(outFut);

      IfStatement ifMapped = null;
      List<Statement> fetchInstBuffer;

      if (mustInitOutputMapping) {
        fetchInstBuffer = instBuffer;
      } else {
        ifMapped = new IfStatement(isMapped.asArg());
        instBuffer.add(ifMapped);
        fetchInstBuffer = new ArrayList<Statement>();
      }

      assert(outFilename != null) : "Expected filename in map for " + outFut;
      Var outFilenameVal;
      if (Types.isString(outFilename)) {
        String valName = block.uniqueVarName(Var.VALUEOF_VAR_PREFIX +
                                           outFilename.name());
        outFilenameVal = WrapUtil.fetchValueOf(block, fetchInstBuffer,
                                     outFilename, valName, false, false);

        if (!mustInitOutputMapping) {
          // Read filename if mapped
          ifMapped.thenBlock().addStatements(fetchInstBuffer);

          // Set filename to something arbitrary if not mapped
          ifMapped.elseBlock().addStatement(ICInstructions.valueSet(outFilenameVal,
                                            Arg.newString("")));
        }
      } else {
        // Already a value
        assert(Types.isStringVal(outFilename));
        outFilenameVal = outFilename;
      }

      instBuffer.add(TurbineOp.initLocalOutFile(outVal,
                            outFilenameVal.asArg(), isMapped.asArg()));
    }
  }

  private static void cleanupLocalOutputVar(Block block, Var outFut, Var outVal) {
    if (Types.isBlobVal(outVal)) {
      block.addCleanup(outVal, TurbineOp.freeBlob(outVal));
    } else if (Types.isFileVal(outVal)) {
      // Cleanup temporary file (if created) if not copied to file future
      if (outFut.isMapped() != Ternary.TRUE &&
          outFut.type().fileKind().supportsTmpImmediate()) {
        block.addCleanup(outVal, TurbineOp.decrLocalFileRef(outVal));
      }
    }
  }


  /**
   * Set futures from output values
   * @param outFuts
   * @param outVals
   * @param instBuffer
   */
  public static void setLocalOpOutputs(Block block,
      List<Var> outFuts, List<Var> outVals,
      List<Statement> instBuffer, boolean storeOutputMapping,
      boolean recursive) {
    if (outFuts == null) {
      assert(outVals == null || outVals.isEmpty());
      return;
    }
    assert(outVals.size() == outFuts.size());
    for (int i = 0; i < outVals.size(); i++) {
      Var outArg = outFuts.get(i);
      Var outVal = outVals.get(i);

      if (outArg.equals(outVal)) {
        // Do nothing: the variable wasn't substituted
      } else {
        assignOutput(block, instBuffer, storeOutputMapping, outArg, outVal,
                     recursive);
      }
    }
  }

  public static void assignOutput(Block block, List<Statement> instBuffer,
      boolean storeOutputMapping, Var outArg, Var outVal, boolean recursive) {
    if (Types.isFile(outArg)) {
      assignOutputFile(block, instBuffer, storeOutputMapping, outArg, outVal);
    } else {
      instBuffer.add(TurbineOp.storeAny(outArg, outVal.asArg(), recursive));
    }
  }

  public static void assignOutputFile(Block block,
      List<Statement> instBuffer, boolean storeOutputMapping,
      Var file, Var fileVal) {
    // Can't be sure if output file is already mapped
    Arg storeFilename;
    if (storeOutputMapping) {
      // Store filename conditional on it not being mapped already
      Var isMapped = block.declareUnmapped(Types.V_BOOL,
          block.uniqueVarName(Var.OPT_VAR_PREFIX + "ismapped"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, VarProvenance.unknown());
      Var storeFilenameV = block.declareUnmapped(Types.V_BOOL,
          block.uniqueVarName(Var.OPT_VAR_PREFIX + "store"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, VarProvenance.unknown());

      instBuffer.add(TurbineOp.isMapped(isMapped, file));
      instBuffer.add(Builtin.createLocal(BuiltinOpcode.NOT,
          storeFilenameV, isMapped.asArg()));
      storeFilename = storeFilenameV.asArg();
    } else {
      // Definitely don't store
      storeFilename = Arg.FALSE;
    }
    instBuffer.add(TurbineOp.assignFile(file, fileVal.asArg(), storeFilename));
  }
}
