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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions;
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
   * TODO: should more fetch recursively?
   */
  public static Var fetchValueOf(Block block, List<? super Instruction> instBuffer,
          Var var, String valName) {
    return fetchValueOf(block, instBuffer, var, valName, false);
  }
  /**

   * Fetch the value of a variable
   * @param block
   * @param instBuffer append fetch instruction to this list
   * @param var the variable to fetch the value of
   * @return variable holding value
   */
  public static Var fetchValueOf(Block block, List<? super Instruction> instBuffer,
          Var var, String valName, boolean recursive) {
    
    Type valueT;
    if (recursive && Types.isContainer(var)) {
      valueT = Types.unpackedContainerType(var);
    } else {
      valueT = Types.derefResultType(var);
    }
    
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
      instBuffer.add(TurbineOp.retrieveRef(deref, var));
      return deref;
    } else if (Types.isContainer(var) && recursive) {
      Var deref = new Var(valueT, valName,
              Alloc.ALIAS, DefType.LOCAL_COMPILER,
              VarProvenance.valueOf(var));
      block.addVariable(deref);
      instBuffer.add(TurbineOp.retrieveRecursive(deref, var));
      return deref;
    } else if (Types.isContainer(var) && !recursive) { 
      Var deref = new Var(valueT, valName,
          Alloc.LOCAL, DefType.LOCAL_COMPILER,
          VarProvenance.valueOf(var));
      block.addVariable(deref);
      // TODO: recursively fetch members?
      if (Types.isArray(var)) {
        instBuffer.add(TurbineOp.retrieveArray(deref, var)); 
      } else {
        assert(Types.isBag(var));
        instBuffer.add(TurbineOp.retrieveBag(deref, var));
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
   * @param mapOutFiles 
   * @return (a list of variables to wait for,
   *          the mapping from file output vars to filenames)
   */
  public static Pair<List<WaitVar>, Map<Var, Var>> buildWaitVars(
      Block block, ListIterator<Statement> instInsertIt,
      List<Var> inArgs, List<Var> outArgs, boolean mapOutFiles) {
    if (inArgs == null) {
      // Gracefully handle null as empty list
      inArgs = Collections.emptyList();
    }
    if (outArgs == null) {
      // Gracefully handle null as empty list
      outArgs = Collections.emptyList();
    }
    
    List<WaitVar> waitVars = new ArrayList<WaitVar>(inArgs.size());
    Map<Var, Var> filenameVars = new HashMap<Var, Var>();
    for (Var in: inArgs) {
      if (!Types.isPrimUpdateable(in.type())) {
        waitVars.add(new WaitVar(in, false));
      }
    }
    
    for (Var out: outArgs) {
      if (Types.isFile(out) && mapOutFiles) {
        // Must wait on filename of output var
        String name = block.uniqueVarName(Var.WRAP_FILENAME_PREFIX +
                                          out.name());
        Var filenameTmp = block.declareUnmapped(Types.F_STRING,
            name, Alloc.ALIAS, DefType.LOCAL_COMPILER,
            VarProvenance.filenameOf(out));
        initOrGetFileName(block, instInsertIt, filenameTmp, out);
        waitVars.add(new WaitVar(filenameTmp, false));
        filenameVars.put(out, filenameTmp);
      }
    }
    return Pair.create(waitVars, filenameVars);
  }

  /**
   * Get the filename for a file, initializing it to a temporary value in
   * the case where it's not mapped.
   * @param block
   * @param filename alias to be initialized
   * @param file
   */
  public static void initOrGetFileName(Block block,
            ListIterator<Statement> insertPos, Var filename, Var file) {
    assert(Types.isString(filename.type()));
    assert(filename.storage() == Alloc.ALIAS);
    assert(Types.isFile(file.type()));
    Instruction getFileName = TurbineOp.getFileName(filename, file);
    
    if (file.isMapped() == Ternary.TRUE ||
        !file.type().fileKind().supportsTmpImmediate()) {
      // Just get the mapping in these cases:
      // - File is definitely mapped
      // - The file type doesn't support temporary creation
      insertPos.add(getFileName);
    } else {
      // Use optimizer var prefixes to avoid clash with any frontend vars
      Var isMapped = getIsMapped(block, insertPos, file);
      
      IfStatement ifMapped = new IfStatement(isMapped.asArg());
      ifMapped.setParent(block);
      
      // Case when mapped: just return var
      ifMapped.thenBlock().addStatement(getFileName);
      
      // Case when not mapped: init with tmp
      Block elseB = ifMapped.elseBlock();
      Var filenameVal = elseB.declareUnmapped(Types.V_STRING,
          OptUtil.optFilenamePrefix(elseB, file), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, VarProvenance.filenameOf(file));
      initTemporaryFileName(elseB.statementEndIterator(), file, filenameVal);
      // Get the filename again but can assume mapping initialized
      elseB.addStatement(getFileName);
      
      insertPos.add(ifMapped);
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
      List<Instruction> instBuffer, boolean uniquifyNames) {
    if (inputs == null) {
      // Gracefully handle null as empty list
      inputs = Collections.emptyList();
    }
    List<Arg> inVals = new ArrayList<Arg>(inputs.size());
    for (Var inArg: inputs) {
      if (Types.isContainer(inArg.type())) {
        // Pass arrays in original representation
        inVals.add(inArg.asArg());
      } else {
        String name = valName(block, inArg, uniquifyNames);
        inVals.add(WrapUtil.fetchValueOf(block, instBuffer,
            inArg, name).asArg());
      }
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
   * @param mapOutFiles 
   * @return
   */
  public static List<Var> createLocalOpOutputs(Block block,
      List<Var> outputFutures, Map<Var, Var> filenameVars,
      List<Instruction> instBuffer, boolean uniquifyNames, boolean mapOutFiles) {
    if (outputFutures == null) {
      // Gracefully handle null as empty list
      outputFutures = Collections.emptyList();
    }
    List<Var> outVals = new ArrayList<Var>();
    for (Var outArg: outputFutures) {
      if (Types.isContainer(outArg.type()) || Types.isPrimUpdateable(outArg.type())) {
        // Use standard representation
        outVals.add(outArg);
      } else {
        outVals.add(WrapUtil.createLocalOutputVar(outArg, filenameVars,
                         block, instBuffer, uniquifyNames, mapOutFiles));
      }
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
          String valName) {
    return block.declareUnmapped(Types.derefResultType(var.type()),
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
   * @param mapOutFile whether to initialize mappings for output files 
   * @return
   */
  public static Var createLocalOutputVar(Var outFut,
      Map<Var, Var> filenameVars,
      Block block, List<Instruction> instBuffer, boolean uniquifyName,
      boolean mapOutFile) {
    String outValName = valName(block, outFut, uniquifyName);
    Var outVal = WrapUtil.declareLocalOutputVar(block, outFut, outValName);
    WrapUtil.initLocalOutputVar(block, filenameVars, instBuffer,
                                outFut, outVal, mapOutFile);
    WrapUtil.cleanupLocalOutputVar(block, outFut, outVal);
    return outVal;
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
      List<Instruction> instBuffer, Var outFut, Var outVal, boolean mapOutFile) {
    if (Types.isFile(outFut) && mapOutFile) {
      // Initialize filename in local variable
      Var outFilename = filenameVars.get(outFut);
      assert(outFilename != null) : "Expected filename in map for " + outFut;
      Var outFilenameVal;
      if (Types.isString(outFilename)) {
        String valName = block.uniqueVarName(Var.OPT_VALUE_VAR_PREFIX +
                                           outFilename.name());
        outFilenameVal = WrapUtil.fetchValueOf(block, instBuffer,
                                                 outFilename, valName);
      } else {
        // Already a value
        assert(Types.isStringVal(outFilename));
        outFilenameVal = outFilename;
      }
      
      Var isMapped = getIsMapped(block, instBuffer.listIterator(), outFut);
      
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
      List<Instruction> instBuffer, boolean setOutVarMapping) {
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
        if (Types.isFile(outArg) && setOutVarMapping) {
          setFilenameFromFileVal(block, instBuffer, outArg, outVal);
        }
        instBuffer.add(ICInstructions.storePrim(outArg, outVal.asArg()));
      }
    }
  }

  public static void setFilenameFromFileVal(Block block,
      List<Instruction> instBuffer, Var fileFut, Var fileVal) {
    assert(Types.isFile(fileFut));
    assert(Types.isFileVal(fileVal));
    String filenameVName = OptUtil.optFilenamePrefix(block, fileFut);  
    Var filenameV = block.declareUnmapped(Types.V_STRING, filenameVName,
          Alloc.LOCAL, DefType.LOCAL_COMPILER, VarProvenance.filenameOf(fileVal));
    instBuffer.add(TurbineOp.getLocalFileName(filenameV, fileVal));
    instBuffer.add(TurbineOp.setFilenameVal(fileFut, filenameV.asArg()));
  }
}
