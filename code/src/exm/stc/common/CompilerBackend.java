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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.type.ArrayType;

import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types.BagType;
import exm.stc.common.lang.Types.FileFutureType;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.ScalarFutureType;
import exm.stc.common.lang.Types.ScalarValueType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

public interface CompilerBackend {
  
  public static class CodeGenOptions {

    private final boolean checkpointRequired; 
  
    public CodeGenOptions(boolean checkpointRequired) {
      this.checkpointRequired = checkpointRequired;
    }
    
    public boolean checkpointRequired() {
      return checkpointRequired;
    }

  }
  
  /**
   * Called once before code generation starts to initialize generation.
   * @param options
   */
  public void initialize(CodeGenOptions options);

  /**
   * Called once after code generation ends to allow generator to finalize
   * before generating actual output code.
   */
  public void finalize();

  /**
   * Generate code, and output to provided stream.
   * @param out output stream
   */
  public void generate(OutputStream out) throws IOException;

  /**
   * A package that must be loaded.
   * 
   * Called before any functions or executable code is generated.
   * @param pkg
   */
  public void requirePackage(RequiredPackage pkg);
  

  /**
   * Add a global constant variable.
   * Called before any functions or executable code is generated.
   * @param name
   * @param val
   */
  public void addGlobalConst(Var var, Arg val);
  
  /**
   * Let backend know about struct type that will be used.
   * Called before any functions or executable code is generated.
   * @param structType
   */
  public void declareStructType(StructType structType);
  
  /**
   * Define a foreign function.
   * TODO: more info? generalize beyond tcl.
   * @param name
   * @param type
   * @param impl tcl function implementing this.  Must be provided
   * @throws UserException
   */
  public void defineForeignFunction(String name, FunctionType type,
                        ForeignFunction impl) throws UserException;
  
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

  /**
   * A variable declaration with associated info.
   */
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
   * Declare and initialize a number of variables.
   * Called before any of these variables are used. 
   * @param decls variable declarations
   */
  public void declare(List<VarDecl> decls);

  /**
   * Start a new nested block scope. Can be a no-op for many backends.
   */
  public void startNestedBlock();

  /**
   * End nested block scope.
   */
  public void endNestedBlock();

  /**
   * Start an if statement and enter the then branch.
   * @param condition the variable name to branch based on (int or bool value type)
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
  public void startIfStatement(Arg condition, boolean hasElse);

  /**
   * Move onto the else block, if any
   */
  public void startElseBlock();
  
  /**
   * End if statement.
   */
  public void endIfStatement();

  /**
   * Start a switch statement and enter the first case
   * @param switchVar must be integer value type
   * @param caseLabels labels for cases
   * @param hasDefault if there's a default case at the end
   */
  public void startSwitch(Arg switchVar,
      List<Integer> caseLabels, boolean hasDefault);

  /**
   * Finish the current case and move to the next, if any.
   */
  public void endCase();

  /**
   * End the switch statement.
   */
  public void endSwitch();

  /**
   * Start a parallel foreach loop over an array.
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

  /**
   * Finish the parallel foreach loop over array.
   * @param splitDegree
   * @param arrayClosed
   * @param perIterDecrs per-iteration decrements at end of block
   */
  public void endForeachLoop(int splitDegree,
            boolean arrayClosed, List<RefCount> perIterDecrs);

  /**
   * A loop over an integer range.  The range can be totally fixed
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
  
  /**
   * Finish the range loop
   * @param splitDegree
   * @param perIterDecrs decrements per iteration at end of loop body
   */
  public void endRangeLoop(int splitDegree, List<RefCount> perIterDecrs);
   
  /**
   * Start code that will execute asynchronously
   * @param procName the name of the wait block (useful so generated
   *                         code can have a nice name for the block)
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
   * that runs as continuation after something executed by some kind of
   * asynchronous execution provider (executor).  Enter the continuation
   * for code generation if present.
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
  
  /**
   * Finish asynchronous executor continuation. 
   * @param hasContinuation
   */
  public void endAsyncExec(boolean hasContinuation);
  
  /**
   * Start ordered loop.
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
  
  /**
   * Run next iteration of ordered loop with new values of loop variables.
   * @param newVals
   * @param usedVariables
   * @param blockingVars
   */
  public void loopContinue(List<Arg> newVals,
      List<Var> usedVariables,
      List<Boolean> blockingVars);
  
  /**
   * Called at end of last iteration of ordered loop.
   * @param loopUsedVars variables from outside loop referred to in loop.
   *              references decremented at loop break
   * @param keepOpenVars
   */
  public void loopBreak(List<Var> loopUsedVars, List<Var> keepOpenVars);
  
  /**
   * Finish ordered loop code generation.
   */
  public void endLoop();

  /**
   * Represents a refcount operation
   * TODO: direction?
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
   * Add comment to output code.  
   * @param comment
   */
  public void addComment(String comment);

  /**
   * Modify a reference count for a variable.  Variable must have a
   * tracked refcount as defined in {@link RefCounting}.
   * @param var variable with refcount to modify
   * @param rcType reference count type
   * @param direction of refcount change
   * @param amount non-negative change in reference count
   */
  public void modifyRefCount(Var var, RefCountType rcType, RCDir dir, Arg amount);
  
  /**
   * Local version of builtin operation operating on local value variables, e.g.
   * {@link ScalarValueType}.
   * @param op builtin operation code
   * @param out output variable
   * @param in input variables or values
   */
  public void localOp(BuiltinOpcode op, Var out, List<Arg> in);
  
  /**
   * Local version of builtin operation operating on shared variables, e.g.
   * {@link ScalarFutureType}.
   * @param op builtin operation code
   * @param out output variable
   * @param in input variables
   */
  public void asyncOp(BuiltinOpcode op, Var out,  List<Arg> in,
                      TaskProps props);  
  
  /**
   * Assign a scalar value to a future.
   * @param dst a scalar future variable (i.e. of {@link ScalarFutureType})
   * @param src a value variable or value (i.e. of {@link ScalarValueType})
   */
  public void assignScalar(Var dst, Arg src);

  /**
   * Retrieve a scalar value from a future
   * @param dst value variable (i.e. of {@link ScalarValueType})
   * @param src future variable (i.e. of {@link ScalarFutureType})
   * @param decr read refcounts to decrement from src
   */
  public void retrieveScalar(Var dst, Var src, Arg decr);

  /**
   * Assign a file future object.  Increment local file ref count.
   * @param dst file future (i.e. of {@link FileFutureType})
   * @param src local file variable (i.e. of {@link FileValueType})
   * @param setFilename if true, set filename on dst (otherwise assume already was set)
   */
  public void assignFile(Var dst, Arg src, Arg setFilename);

  /**
   * Retrieve local file info from a file future object
   * @param dst local file variable (i.e. of {@link FileValueType})
   * @param src file future (i.e. of {@link FileFutureType})
   * @param decr read refcounts to decrement from src
   */
  public void retrieveFile(Var dst, Var src, Arg decr);
  
  /**
   * Set target=addressof(src)
   * @param dst reference future type (i.e. of {@link RefType})
   * @param src any type of shared data that we can have a reference to
   * @param readRefs Number of read refs to transfer to reference var 
   * @param writeRefs Number of write refs to transfer to reference var
   */
  public void assignReference(Var dst, Var src,
                              long readRefs, long writeRefs);

  /**
   * Retrieve value of reference
   * @param dst var of dereferenced type
   * @param src var of {@link RefType}
   * @param acquireRead how many refcounts to contents of src to acquire
   * @param acquireWrite how many refcounts for contents of src to acquire
   * @param decr read refcounts to decrement from src
   */
  public void retrieveRef(Var dst, Var src, Arg acquireRead,
                          Arg acquireWrite, Arg decr);

  /**
   * Store contents of array
   * @param dst shared var of {@link ArrayType}
   * @param src local var of {@link ArrayType}
   */
  public void assignArray(Var dst, Arg src);
  
  /**
   * Retrieve contents of array to dst
   * @param dst local var of {@link ArrayType}
   * @param src shared var of {@link ArrayType}
   * @param decr read refcounts to decrement from src
   */
  public void retrieveArray(Var dst, Var src, Arg decr);

  /**
   * Store contents of bag
   * @param dst shared var of {@link BagType}
   * @param src local var of {@link BagType}
   */
  public void assignBag(Var dst, Arg src);

  /**
   * Retrieve contents of local bag representation to dst
   * @param dst local var of {@link BagType}
   * @param src shared var of {@link BagType}
   * @param decr read refcounts to decrement from src
   */
  public void retrieveBag(Var dst, Var src, Arg decr);

  /**
   * Store contents of struct
   * @param dst shared var of {@link StructType}
   * @param src local var of {@link StructType}
   */
  public void assignStruct(Var dst, Arg src);

  /**
   * Retrieve contents of local struct representation to dst
   * @param dst local var of {@link StructType}
   * @param src shared var of {@link StructType}
   * @param decr read refcounts to decrement from src
   */
  public void retrieveStruct(Var target, Var src, Arg decr);

  public void assignRecursive(Var target, Arg src);

  public void retrieveRecursive(Var target, Var src, Arg decr);

  public void dereferenceScalar(Var dst, Var src);
  
  public void dereferenceFile(Var dst, Var src);

  /**
   * Copy the handle to a future, creating an alias
   * @param dst
   * @param src
   */
  public void makeAlias(Var dst, Var src);

  
  public void structInitFields(Var struct, List<List<String>> fieldPaths,
                               List<Arg> fieldVals, Arg writeDecr);
  
  public void buildStructLocal(Var struct, List<List<String>> fieldPaths,
                                List<Arg> fieldVals); 
  
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
  public void getFileNameAlias(Var filename, Var file);
  
  /**
   * Copy filename from future to file
   */
  public void copyInFilename(Var file, Var filename);
  
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
   * Get filename of file future to a local string value
   * @param file file future
   * @param filenameVal a local string value
   */
  public void getFilenameVal(Var filenameVal, Var file);
  
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
  
  public void structCreateAlias(Var output, Var struct,
                                List<String> fields);
  public void structRetrieveSub(Var output, Var struct,
      List<String> fields, Arg readDecr);
  public void structCopyOut(Var output, Var struct,
      List<String> fields);
  
  public void structRefCopyOut(Var result, Var structVar,
                              List<String> fields);

  /**
   * Copy in value of variable to struct field 
   * @param struct
   * @param fieldName
   * @param fieldContents
   */

  public void structStore(Var struct, List<String> fields,
                           Arg fieldContents);
  public void structCopyIn(Var struct, List<String> fields,
      Var fieldContents);
  public void structRefStoreSub(Var structRef, List<String> fields,
                           Arg fieldContents);
  public void structRefCopyIn(Var structRef, List<String> fields,
                           Var fieldContents);
  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param oVar
   * @param arrayVar
   * @param arrayIndex
   * @param readDecr
   */
  public void arrayRetrieve(Var oVar, Var arrayVar,
                            Arg arrayIndex, Arg readDecr);

  public void arrayCreateAlias(Var oVar, Var arrayVar, Arg arrayIndex);
  
  public void arrayCopyOutImm(Var oVar, Var arrayVar, Arg arrayIndex);

  public void arrayCopyOutFuture(Var oVar, Var arrayVar, Var indexVar);
  
  public void arrayRefCopyOutImm(Var oVar, Var arrayVar, Arg arrayIndex);

  public void arrayRefCopyOutFuture(Var oVar, Var arrayVar, Var indexVar);

  public void arrayContains(Var out, Var arr, Arg index);

  public void containerSize(Var out, Var cont);

  public void arrayLocalContains(Var out, Var arr, Arg index);

  public void containerLocalSize(Var out, Var cont);
  
  public void arrayStoreFuture(Var array,
      Var ix, Arg member, Arg writersDecr);
  

  public void arrayCopyInFuture(Var array,
      Var ix, Var member, Arg writersDecr);
  
  public void arrayRefStoreFuture(Var array, Var ix, Arg member);
  
  public void arrayRefCopyInFuture(Var array, Var ix, Var member);

  public void arrayStore(Var array, Arg ix, Arg member,
      Arg writersDecr);

  public void arrayCopyInImm(Var array, Arg ix, Var member,
      Arg writersDecr);
  
  public void arrayRefStoreImm(Var array, Arg ix, Arg member);
  
  public void arrayRefCopyInImm(Var array, Arg ix, Var member);


  /**
   * Build array with specified key-value pairs 
   * @param array
   * @param keys
   * @param vals
   */
  public void arrayBuild(Var array, List<Arg> keys, List<Arg> vals);


  /**
   * Copy non-closed non-local data
   * @param dst
   * @param src
   */
  public void asyncCopy(Var dst, Var src);

  /**
   * Copy closed non-local data synchronously
   * @param dst
   * @param src
   */
  public void syncCopy(Var dst, Var src);
  
  public void arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix);

  /**
   * Create a nested array inside an array
   * @param arrayResult
   * @param array
   * @param ix
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void arrayCreateNestedImm(Var arrayResult,
      Var array, Arg ix, Arg callerReadRefs, Arg callerWriteRefs,
      Arg readDecr, Arg writeDecr);

  public void arrayRefCreateNestedFuture(Var arrayResult, Var array, Var ix);

  public void arrayRefCreateNestedImm(Var arrayResult, Var array, Arg ix);

  public void bagInsert(Var bag, Arg elem, Arg writersDecr);

  public void arrayCreateBag(Var bag, Var arr, Arg ix, Arg callerReadRefs,
                        Arg callerWriteRefs, Arg readDecr, Arg writeDecr);

  public void initUpdateable(Var updateable, Arg val);
  public void latestValue(Var result, Var updateable);
  
  public void update(Var updateable, Operators.UpdateMode updateMode,
                              Var val);
  /** Same as above, but takes a value or constant as arg */
  public void updateImm(Var updateable, Operators.UpdateMode updateMode,
      Arg val);

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