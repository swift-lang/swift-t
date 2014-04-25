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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;

public interface CompilerBackend {

  public void header();

  public void turbineStartup(boolean checkpointRequired);

  public void requirePackage(String pkg, String version);
  
  public void declareStructType(StructType st);
  
  public static class VarDecl {
    public VarDecl(Var var, Arg initReaders, Arg initWriters) {
      this.var = var;
      this.initReaders = initReaders;
      this.initWriters = initWriters;
    }
    
    public final Var var;
    public final Arg initReaders;
    public final Arg initWriters;
  }
  
  /**
   * Declare and initialize a number of variables
   */
  public void declare(List<VarDecl> decls);

  public void decrRef(Var var, Arg amount);

  public void incrRef(Var var, Arg amount);
  
  public void decrWriters(Var var, Arg amount);

  public void incrWriters(Var var, Arg amount);


  public void localOp(BuiltinOpcode op, Var out, 
                                            List<Arg> in);
  
  public void asyncOp(BuiltinOpcode op, Var out, 
                               List<Arg> in, TaskProps props);  
  
  /**
   * Set target=addressof(src)
   */
  public void assignReference(Var target, Var src);

  public void dereferenceInt(Var target, Var src);
  
  public void dereferenceBool(Var target, Var src);
  
  public void dereferenceVoid(Var target, Var src);

  public void dereferenceFloat(Var dst, Var src);
  
  public void dereferenceBlob(Var dst, Var src);
  
  public void dereferenceFile(Var dst, Var src);

  public void retrieveRef(Var target, Var src, Arg decr);
  
  /**
   * Copy the handle to a future, creating an alias
   * @param dst
   * @param src
   */
  public void makeAlias(Var dst, Var src);

  public void dereferenceString (Var target, Var src);

  /**assignInt, which can take a value variable or a literal int in oparg
   */
  public void assignInt(Var target, Arg src);
  public void retrieveInt(Var target, Var source, Arg decr);

  public void assignVoid(Var target, Arg src);
  /**
   * Used to represent dataflow dependency.  Sets target to
   * arbitrary value
   * @param target
   * @param source
   */
  public void retrieveVoid(Var target, Var source, Arg decr);
  
  public void assignFloat(Var target, Arg src);
  public void retrieveFloat(Var target, Var source, Arg decr);

  /** assignString, which can take a value variable or a literal int in oparg
   */
  public void assignString(Var target, Arg src);

  public void retrieveString(Var target, Var source, Arg decr);
  
  public void assignBool(Var target, Arg src);
  public void retrieveBool(Var target, Var source, Arg decr);

  public void assignBlob(Var target, Arg src);
  public void retrieveBlob(Var target, Var src, Arg decr);
  
  /**
   * Set file object.  Increment local file ref count
   * @param target
   * @param src dummy local variable
   */
  public void assignFile(Var target, Arg src);

  public void retrieveFile(Var target, Var src, Arg decr);
  
  public void assignArray(Var target, Arg src);

  public void retrieveArray(Var target, Var src, Arg decr);
  
  public void assignBag(Var target, Arg src);

  public void retrieveBag(Var target, Var src, Arg decr);
  
  public void assignRecursive(Var target, Arg src);
  
  public void retrieveRecursive(Var target, Var src, Arg decr);

  /**
   * Used to cleanup local file if needed
   * @param fileVal
   */
  public void decrLocalFileRef(Var fileVal);

  /**
   * Free local blob value
   * @param blobval
   */
  public void freeBlob(Var blobval);
  
  /**
   * Extract handle to filename future out of file variable
   */
  public void getFileName(Var filename, Var file);
  
  /**
   * Extract handle to filename future out of localfile variable
   */
  public void getLocalFileName(Var filename, Var file);
  
  /**
   * Determine if a file is mapped
   * @param isMapped a local boolean var
   * @param file
   */
  public void isMapped(Var isMapped, Var file);
  
  /**
   * Choose a temporary file name
   * @param filenameVal
   */
  public void chooseTmpFilename(Var filenameVal);
  

  /**
   * Initialise a local file with a filename
   * @param localFile an uninitialized local file var
   * @param filenameVal an immediate string containing the filename
   * @param isMapped an immediate bool saying whether the file is mapped
   *                - i.e. whether it must be retained in all cases
   */
  public void initLocalOutputFile(Var localFile, Arg filenameVal,
                                  Arg isMapped);
  
  /**
   * Set filename of file future to a local string value
   * @param file file future
   * @param filenameVal a local string value
   */
  public void setFilenameVal(Var file, Arg filenameVal);

  /**
   * Copy file contents for files represented by local file values
   * @param dst an output file value initialised with file name
   * @param src an input file value initialised with file name
   */
  public void copyFileContents(Var dst, Var src);

  /**
   * NOTE: all built-ins should be defined before other functions
   * @param function
   * @param inputs
   * @param outputs
   * @param props 
   */
  public void builtinFunctionCall(String function,
      List<Arg> inputs, List<Var> outputs, TaskProps props);

  public void functionCall(String function,
      List<Arg> inputs, List<Var> outputs, List<Boolean> blockOn, 
      TaskMode mode, TaskProps props);

  public void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Var> outputs);
  
  /**
   * Generate command to run an external application immediately
   * @param redirects 
   */
  public void runExternal(String cmd, List<Arg> args,
           List<Arg> inFiles, List<Var> outFiles, 
           Redirects<Arg> redirects,
           boolean hasSideEffects, boolean deterministic);
  
  /**
   * lookup structVarName.structField and copy to oVarName
   * @param result
   * @param structVar
   * @param structField
   */
  public void structLookup(Var result, Var structVar,
      String structField);
  
  public void structRefLookup(Var result, Var structVar,
      String fieldName);

  public void structInitField(Var structVar, String fieldName,
                                          Var fieldContents);

  public void arrayLookupFuture(Var oVar, Var arrayVar,
      Var indexVar, boolean isArrayRef);

  public void arrayLookupRefImm(Var oVar, Var arrayVar,
      Arg arrayIndex, boolean isArrayRef);
  
  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param oVar
   * @param arrayVar
   * @param arrayIndex
   */
  public void arrayLookupImm(Var oVar, Var arrayVar,
      Arg arrayIndex);

  public void arrayInsertFuture(Var array,
      Var ix, Var member, Arg writersDecr);
  

  public void arrayDerefInsertFuture(Var array,
      Var ix, Var member, Arg writersDecr);
  
  public void arrayRefInsertFuture(Var outerArray,
      Var array, Var ix, Var member);
  
  public void arrayRefDerefInsertFuture(Var outerArray,
      Var array, Var ix, Var member);

  public void arrayInsertImm(Var array, Arg ix, Var member,
      Arg writersDecr);

  public void arrayDerefInsertImm(Var array, Arg ix, Var member,
      Arg writersDecr);
  
  public void arrayRefInsertImm(Var outerArray, 
      Var array, Arg ix, Var member);
  
  public void arrayRefDerefInsertImm(Var outerArray, 
      Var array, Arg ix, Var member);


  /**
   * Build array with specified key-value pairs 
   * @param array
   * @param keys
   * @param vals
   */
  public void arrayBuild(Var array, List<Arg> keys, List<Var> vals);

  public void arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix);

  /**
   * Create a nested array inside an array
   * @param arrayResult
   * @param array
   * @param ix
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   */
  public void arrayCreateNestedImm(Var arrayResult,
      Var array, Arg ix, Arg callerReadRefs, Arg callerWriteRefs);

  public void arrayRefCreateNestedFuture(Var arrayResult,
      Var outerArray, Var array, Var ix);

  public void arrayRefCreateNestedImm(Var arrayResult,
      Var outerArray, Var array, Arg ix);

  public void bagInsert(Var bag, Var elem, Arg writersDecr);

  public void arrayCreateBag(Var bag, Var arr, Arg ix, Arg callerReadRefs,
                              Arg callerWriteRefs);

  public void initUpdateable(Var updateable, Arg val);
  public void latestValue(Var result, Var updateable);
  
  public void update(Var updateable, Operators.UpdateMode updateMode,
                              Var val);
  /** Same as above, but takes a value or constant as arg */
  public void updateImm(Var updateable, Operators.UpdateMode updateMode,
      Arg val);
  
  /**
   * @param name
   * @param type
   * @param impl tcl function implementing this.  Must be provided
   * @throws UserException
   */
  public void defineBuiltinFunction(String name,
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
  public void startFunction(String functionName,
      List<Var> oList, List<Var> iList, TaskMode mode)
            throws UserException;

  public void endFunction();

  public void startNestedBlock();

  public void endNestedBlock();

  public void addComment(String comment);

  /**
   * @param condition the variable name to branch based on (int or bool value type)
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
  public void startIfStatement(Arg condition, boolean hasElse);

  public void startElseBlock();

  public void endIfStatement();

  /**
   * 
   * @param switchVar must be integer value type
   * @param caseLabels
   * @param hasDefault
   */
  public void startSwitch(Arg switchVar,
      List<Integer> caseLabels, boolean hasDefault);

  public void endCase();

  public void endSwitch();

  
  /**
   * Represents a refcount operation
   */
  public static class RefCount {
    public final Var var;
    public final RefCountType type;
    public final Arg amount;
    
    public RefCount(Var var, RefCountType type, Arg amount) {
      super();
      this.var = var;
      this.type = type;
      this.amount = amount;
    }
    
    @Override
    public String toString() {
      return var.name() + "<" + type.toString() + ">" + amount;
    }
    
    public static final List<RefCount> NONE = Collections.emptyList();
  }

  /**
   * 
   * @param loopName unique name for loop
   * @param container
   * @param memberVar
   * @param loopCountVar counter variable, can be null
   * @param splitDegree
   * @param leafDegree 
   * @param arrayClosed if true, assume array is already closed
   * @param passedVars
   * @param perIterIncrs per-iteration increments
   * @param constIncrs constant increments
   */
  public void startForeachLoop(String loopName,
      Var container, Var memberVar, Var loopCountVar, int splitDegree,
      int leafDegree, boolean arrayClosed,
      List<PassedVar> passedVars, List<RefCount> perIterIncrs,
      MultiMap<Var, RefCount> constIncrs);

  public void endForeachLoop(int splitDegree,
            boolean arrayClosed, List<RefCount> perIterDecrements);

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
   * @param passedVars variables used in loop body
   * @param splitDegree the desired loop split factor (negative if no splitting)
   * @param perIterIncrs per-iteration increments
   * @param constIncrs constant increments
   */
  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment, int splitDegree, int leafDegree, 
      List<PassedVar> passedVars, List<RefCount> perIterIncrs,
      MultiMap<Var, RefCount> constIncrs);
  public void endRangeLoop(int splitDegree, List<RefCount> perIterDecrs);
  /**
   * Add a global variable (currently constant literals are supported)
   * @param name
   * @param val
   */
  public void addGlobal(String name, Arg val);
   
  /**
     Generate and return Tcl from our internal TclTree
   */
  public String code();
  
  /**
   * Different kinds of wait statements that can be optimized in
   * different ways
   */
  public static enum WaitMode {
    WAIT_ONLY, /* Used to defer execution of block until data closed */
    TASK_DISPATCH; /* Used to dispatch async task to 
    load balancer/other node */
  }
  
  /**
   * Start code that will execute asynchronously
   * @param procName the name of the wait block (useful so generated
   *    tcl code can have a nice name for the block)
   * @param waitVars
   * @param usedVars any variables which are read or written inside block
   * @param target if true, wait until all contents of arrays/structs
   *                   (recursively) are closed
   * @param props controls where asynchronous execution occurs
   */
  public void startWaitStatement(String procName,
      List<Var> waitVars,
      List<Var> usedVars, boolean recursive,
      TaskMode target, TaskProps props);

  public void endWaitStatement();

  /**
   * Start asynchronous execution construct that encloses code block
   * that runs as continuation.
   * @param procName
   * @param executor
   * @param cmdName 
   * @param taskOutputs
   * @param taskArgs
   * @param taskProps
   * @param hasContinuation true if we have a continuation to run after task
   */
  public void startAsyncExec(String procName, List<Var> passIn, 
      AsyncExecutor executor, String cmdName, List<Var> taskOutputs,
      List<Arg> taskArgs, Map<String, Arg> taskProps,
      boolean hasContinuation);
  
  public void endAsyncExec(boolean hasContinuation);
  
  /**
   * 
   * @param loopName
   * @param loopVars first one is loop condition
   * @param initVals initial values for loop variables
   * @param usedVariables
   * @param keepOpenVars
   * @param initWaitVars values to wait for before executing first iteration
   * @param simpleLoop if this is a simple loop that does not require waiting
   *                    between iterations 
   */
  public void startLoop(String loopName, List<Var> loopVars,
      List<Boolean> definedHere, List<Arg> initVals, List<Var> usedVariables,
      List<Var> keepOpenVars, List<Var> initWaitVars,
      boolean simpleLoop);
  
  public void loopContinue(List<Arg> newVals,
      List<Var> usedVariables,
      List<Boolean> blockingVars);
  /**
   * @param loopUsedVars variables from outside loop referred to in loop.
   *              references decremented at loop break
   * @param keepOpenVars
   */
  public void loopBreak(List<Var> loopUsedVars, List<Var> keepOpenVars);
  public void endLoop();

  public void checkpointLookupEnabled(Var out);
  
  public void checkpointWriteEnabled(Var out);
  
  public void writeCheckpoint(Arg key, Arg val);

  public void lookupCheckpoint(Var checkpointExists, Var value, Arg key);

  public void packValues(Var packed, List<Arg> unpacked);
  
  public void unpackValues(List<Var> unpacked, Arg packed);

  /**
   * Unpack a nested array into a local flat array 
   * @param flatLocalArray
   * @param inputArray
   */
  public void unpackArrayToFlat(Var flatLocalArray, Arg inputArray);

}