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
package exm.stc.common;

import java.util.List;

import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Var;

public interface CompilerBackend {

  public abstract void header();

  public abstract void turbineStartup();

  public abstract void requirePackage(String pkg, String version);
  
  /**
   * @param var variable object
   * @throws UndefinedTypeException
   */
  public abstract void declare(Var var,
      Arg initReaders, Arg initWriters) throws UndefinedTypeException;

  public abstract void decrRef(Var var, Arg amount);

  public abstract void incrRef(Var var, Arg amount);
  
  public abstract void decrWriters(Var var, Arg amount);

  public abstract void incrWriters(Var var, Arg amount);


  public abstract void localOp(BuiltinOpcode op, Var out, 
                                            List<Arg> in);
  
  public abstract void asyncOp(BuiltinOpcode op, Var out, 
                               List<Arg> in, Arg priority);  
  
  /**
   * Set target=addressof(src)
   */
  public abstract void assignReference(Var target, Var src);

  public abstract void dereferenceInt(Var target, Var src);
  
  public abstract void dereferenceBool(Var target, Var src);

  public abstract void dereferenceFloat(Var dst, Var src);
  
  public abstract void dereferenceBlob(Var dst, Var src);
  
  public abstract void dereferenceFile(Var dst, Var src);

  public abstract void retrieveRef(Var target, Var src, Arg decr);
  
  /**
   * Copy the handle to a future, creating an alias
   * @param dst
   * @param src
   */
  public abstract void makeAlias(Var dst, Var src);

  public abstract void dereferenceString (Var target, Var src);

  /**assignInt, which can take a value variable or a literal int in oparg
   */
  public abstract void assignInt(Var target, Arg src);
  public abstract void retrieveInt(Var target, Var source, Arg decr);

  public abstract void assignVoid(Var target, Arg src);
  /**
   * Used to represent dataflow dependency.  Sets target to
   * arbitrary value
   * @param target
   * @param source
   */
  public abstract void retrieveVoid(Var target, Var source, Arg decr);
  
  public abstract void assignFloat(Var target, Arg src);
  public abstract void retrieveFloat(Var target, Var source, Arg decr);

  /** assignString, which can take a value variable or a literal int in oparg
   */
  public abstract void assignString(Var target, Arg src);

  public abstract void retrieveString(Var target, Var source, Arg decr);
  
  public abstract void assignBool(Var target, Arg src);
  public abstract void retrieveBool(Var target, Var source, Arg decr);

  public abstract void assignBlob(Var target, Arg src);
  public abstract void retrieveBlob(Var target, Var src, Arg decr);
  
  /**
   * Set file object.  Increment local file ref count
   * @param target
   * @param src dummy local variable
   */
  public abstract void assignFile(Var target, Arg src);

  public abstract void retrieveFile(Var target, Var src, Arg decr);
  

  /**
   * Used to cleanup local file if needed
   * @param fileVal
   */
  public abstract void decrLocalFileRef(Var fileVal);
  
  /**
   * Decrement reference count for cached blob
   * @param blob handle to future
   */
  public abstract void decrBlobRef(Var blob);
  

  /**
   * Free local blob value
   * @param blobval
   */
  public abstract void freeBlob(Var blobval);
  
  /**
   * Extract handle to filename future out of file variable
   * @param initUnmapped if true, assign arbitrary filename to unmapped files
   */
  public abstract void getFileName(Var filename, Var file, boolean initUnmapped);
  
  /**
   * Choose a temporary file name
   * @param filenameVal
   */
  public abstract void chooseTmpFilename(Var filenameVal);
  
  /**
   * Set filename of file future to a local string value
   * @param file file future
   * @param filenameVal a local string value
   */
  public abstract void setFilenameVal(Var file, Arg filenameVal);

  /**
   * NOTE: all built-ins should be defined before other functions
   * @param function
   * @param inputs
   * @param outputs
   * @param priority 
   */
  public abstract void builtinFunctionCall(String function,
      List<Var> inputs, List<Var> outputs, Arg priority);

  public abstract void functionCall(String function,
      List<Var> inputs, List<Var> outputs, List<Boolean> blockOn, 
      TaskMode mode, Arg priority);

  public abstract void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Var> outputs);
  
  /**
   * Generate command to run an external application immediately
   * @param redirects 
   */
  public abstract void runExternal(String cmd, List<Arg> args,
           List<Arg> inFiles, List<Var> outFiles, List<Arg> outFileNames, 
           Redirects<Arg> redirects,
           boolean hasSideEffects, boolean deterministic);
  
  /**
   * lookup structVarName.structField and copy to oVarName
   * @param result
   * @param structVar
   * @param structField
   */
  public abstract void structLookup(Var result, Var structVar,
      String structField);
  
  public abstract void structRefLookup(Var result, Var structVar,
      String fieldName);

  public abstract void structClose(Var struct);

  public abstract void structInsert(Var structVar, String fieldName,
                                          Var fieldContents);

  public abstract void arrayLookupFuture(Var oVar, Var arrayVar,
      Var indexVar, boolean isArrayRef);

  public abstract void arrayLookupRefImm(Var oVar, Var arrayVar,
      Arg arrayIndex, boolean isArrayRef);
  
  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param oVar
   * @param arrayVar
   * @param arrayIndex
   */
  public abstract void arrayLookupImm(Var oVar, Var arrayVar,
      Arg arrayIndex);

  public abstract void arrayInsertFuture(Var array,
      Var ix, Var member, Arg writersDecr);
  
  public abstract void arrayRefInsertFuture(Var outerArray,
      Var array, Var ix, Var member);

  /**
   * Build array with indices [0..members.size() - 1] comprised of the
   * variables from members 
   * @param array
   * @param members
   */
  public abstract void arrayBuild(Var array, List<Var> members);
  
  public abstract void arrayInsertImm(Var array, Arg ix, Var member,
      Arg writersDecr);
  
  public abstract void arrayRefInsertImm(Var outerArray, 
      Var array, Arg ix, Var member);

  public abstract void arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix);

  public abstract void arrayCreateNestedImm(Var arrayResult,
      Var array, Arg ix);

  public abstract void arrayRefCreateNestedFuture(Var arrayResult,
      Var outerArray, Var array, Var ix);

  public abstract void arrayRefCreateNestedImm(Var arrayResult,
      Var outerArray, Var array, Arg ix);

  public abstract void initUpdateable(Var updateable, Arg val);
  public abstract void latestValue(Var result, Var updateable);
  
  public abstract void update(Var updateable, Operators.UpdateMode updateMode,
                              Var val);
  /** Same as above, but takes a value or constant as arg */
  public abstract void updateImm(Var updateable, Operators.UpdateMode updateMode,
      Arg val);
  
  /**
   * @param name
   * @param type
   * @param impl tcl function implementing this.  Must be provided
   * @throws UserException
   */
  public abstract void defineBuiltinFunction(String name,
                FunctionType type, TclFunRef impl) throws UserException;
  
  /**
   * 
   * @param functionName
   * @param oList
   * @param iList
   * @param mode the context the function will run in (e.g. SYNC if
   *        called synchronously).  This is needed for optimizer correctness.
   * @throws UserException
   */
  public abstract void startFunction(String functionName,
      List<Var> oList, List<Var> iList, TaskMode mode)
            throws UserException;

  public abstract void endFunction();

  public abstract void startNestedBlock();

  public abstract void endNestedBlock();

  public abstract void addComment(String comment);

  /**
   * @param condition the variable name to branch based on (int or bool value type)
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
  public abstract void startIfStatement(Arg condition, boolean hasElse);

  public abstract void startElseBlock();

  public abstract void endIfStatement();

  /**
   * 
   * @param switchVar must be integer value type
   * @param caseLabels
   * @param hasDefault
   */
  public abstract void startSwitch(Arg switchVar,
      List<Integer> caseLabels, boolean hasDefault);

  public abstract void endCase();

  public abstract void endSwitch();

  /**
   * 
   * @param loopName unique name for loop
   * @param arrayVar
   * @param memberVar
   * @param loopCountVar counter variable, can be null
   * @param splitDegree
   * @param arrayClosed if true, assume array is already closed
   * @param usedVariables
   * @param keepOpenVars
   */
  public abstract void startForeachLoop(String loopName,
      Var arrayVar, Var memberVar, Var loopCountVar, int splitDegree,
      boolean arrayClosed, List<Var> usedVariables, List<Var> keepOpenVars);

  public abstract void endForeachLoop(int splitDegree, 
            boolean arrayClosed, List<Var> usedVars, List<Var> keepOpenVars);

  
  /**
   * A loop over a prespecified range.  The range can be totally fixed
   *   ( the bounds might be literal integers) or can vary at runtime (
   *   ( in which case it is specified by integer value variables )
   *   The loop construct should run immediately, but have the loop iterations
   *   run in parallel
   * @param loopName unique name for loop
   * @param loopVar variable (integer value) used to store iteration parameter
   * @param countVar variable (integer value) used to store iteration number starting
   *                from 0 (optional) 
   * @param start start (inclusive) of the loop: should be int or int value var
   * @param end end (inclusive) of the loop: should be int or int value var
   * @param increment increment of the loop: should be int or int value var
   * @param usedVariables variables used in loop body
   * @param keepOpenVars vars to keep open for assignment in loop body
   * @param desiredUnroll the suggested unrolling factor
   * @param splitDegree the desired loop split factor (negative if no splitting)
   */
  public abstract void startRangeLoop(String loopName, Var loopVar,
      Var countVar, Arg start, Arg end, Arg increment, List<Var> usedVariables, 
      List<Var> keepOpenVars, int desiredUnroll, int splitDegree);
  public abstract void endRangeLoop(List<Var> usedVars,
              List<Var> keepOpenVars, int splitDegree);
  /**
   * Add a global variable (currently constant literals are supported)
   * @param name
   * @param val
   */
  public abstract void addGlobal(String name, Arg val);
   
  /**
     Generate and return Tcl from our internal TclTree
   */
  public abstract String code();
  
  /**
   * Different kinds of wait statements that can be optimized in
   * different ways
   */
  public static enum WaitMode {
    DATA_ONLY, /* Used to allow data load inside wait */
    EXPLICIT, /* Explicit synchronisation on variable, 
           can only eliminate if var closed */
    TASK_DISPATCH; /* Used to dispatch async task to 
    load balancer/other node */
  }
  
  /**
   * Start code that will execute asynchronously
   * @param procName the name of the wait block (useful so generated
   *    tcl code can have a nice name for the block)
   * @param waitVars
   * @param usedVars any variables which are read or written inside block
   * @param keepOpenVars any vars that need to be kept open for wait
   * @param mode what guarantees wait statement should provide
   * @param recursive if true, wait until all contents of arrays/structs
   *                   (recursively) are closed
   * @param target controls where asynchronous execution occurs
   */
  public abstract void startWaitStatement(String procName,
      List<Var> waitVars,
      List<Var> usedVars, List<Var> keepOpenVars, Arg priority,
      WaitMode mode, boolean recursive, TaskMode target);

  public abstract void endWaitStatement(
      List<Var> waitVars, List<Var> usedVars, List<Var> keepOpenVars);

  
  /**
   * 
   * @param loopName
   * @param loopVars first one is loop condition
   * @param initVals initial values for loop variables
   * @param usedVariables
   * @param keepOpenVars
   * @param blockingVars
   */
  public abstract void startLoop(String loopName, List<Var> loopVars,
      List<Boolean> definedHere, List<Var> initVals, List<Var> usedVariables,
      List<Var> keepOpenVars, List<Boolean> blockingVars);
  
  public abstract void loopContinue(List<Var> newVals,
      List<Var> usedVariables, List<Var> keepOpenVars,
      List<Boolean> blockingVars);
  /**
   * @param loopUsedVars variables from outside loop referred to in loop.
   *              references decremented at loop break
   * @param keepOpenVars
   */
  public abstract void loopBreak(List<Var> loopUsedVars, List<Var> keepOpenVars);
  public abstract void endLoop();
}