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

import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.LocalForeignFunction;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.RequiredPackage;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.BagType;
import exm.stc.common.lang.Types.FileFutureType;
import exm.stc.common.lang.Types.FileValueType;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.ScalarFutureType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.ScalarValueType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WrappedForeignFunction;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

/**
 * The generic interface for a code generation backend to the compiler.
 *
 * TODO: better document which operations consume/return reference counts.
 * Generally asynchronous operations consume reference counts (read/write
 * depending on whether modified or not) by default, while synchronous
 * operations will not consume reference counts aside from when explicitly
 * told to.
 */
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
   * @param information about foreign functions
   */
  public void initialize(CodeGenOptions options, ForeignFunctions foreignFuncs);

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
   * Declare all writable global variable.
   *
   * Called once before any functions or executable code is generated.
   *
   * @param name
   */
  public void declareGlobalVars(List<VarDecl> vars);

  /**
   * Let backend know about struct type that will be used.
   * Called before any functions or executable code is generated.
   * @param structType
   */
  public void declareStructType(StructType structType);

  /**
   * Declare a new work type that behaves the same as, but is separate from
   * the regular work context.  Not all backends will implement this.
   * This will only be called for custom work types outside of the regular
   * control/work contexts and builtin async executors.  It should be called
   * only once for any custom work type that is declared in the program.
   * @param workType
   */
  public void declareWorkType(WorkContext workType);

  /**
   * Define a foreign function.
   * @param name
   * @param type
   * @param localImpl implementation of function that can be called directly
   * @param wrappedImpl wrapped version of function, may be null
   * @throws UserException
   */
  public void defineForeignFunction(FnID name, FunctionType type,
        LocalForeignFunction localImpl, WrappedForeignFunction wrappedImpl)
        throws UserException;

  /**
   * Start a function and enter main block
   * @param id
   * @param outArgs function output arguments
   * @param inArgs function input arguments
   * @param mode the context the function will run in (e.g. SYNC if
   *        called synchronously).  This is needed for optimizer correctness.
   * @throws UserException
   */
  public void startFunction(FnID id,
      List<Var> outArgs, List<Var> inArgs, ExecTarget mode)
            throws UserException;

  /**
   * End code generation for function.
   */
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
      ExecTarget target, TaskProps props);

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
      AsyncExecutor executor, Arg
      cmdName, List<Var> taskOutputs,
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
   * @param usedVariables variables from outer scope used in loop
   * @param keepOpenVars variable to keep open from one iteration to the next
   * @param initWaitVars values to wait for before executing first iteration
   * @param simpleLoop if this is a simple loop that does not require waiting
   *                    between iterations
   */
  public void startLoop(String loopName, List<Var> loopVars,
      List<Arg> initVals, List<Var> usedVariables,
      List<Var> initWaitVars, boolean simpleLoop);

  /**
   * Run next iteration of ordered loop with new values of loop variables.
   * @param newVals new values for loop variable
   * @param usedVariables variables used in next iteration of loop
   * @param blockingVars variables we should wait for before starting next
   *                      iteration
   */
  public void loopContinue(List<Arg> newVals,
      List<Var> usedVariables, List<Boolean> blockingVars);

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
   * Represents a refcount operation.
   * Direction is implied by context.
   * Variables must have a tracked refcount as defined in {@link RefCounting}.
   */
  public static class RefCount {
    public final Var var;
    public final RefCountType type;
    public final Arg amount;

    public RefCount(Var var, RefCountType type, Arg amount) {
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
   * Represents a refcount operation with explicit direction.
   * Variables must have a tracked refcount as defined in {@link RefCounting}.
   */
  public static class DirRefCount {
    public final Var var;
    public final RefCountType type;
    public final RCDir dir;
    public final Arg amount;

    public DirRefCount(Var var, RefCountType type, RCDir dir, Arg amount) {
      this.var = var;
      this.type = type;
      this.dir = dir;
      this.amount = amount;
    }

    @Override
    public String toString() {
      return var.name() + "<" + type.toString() + ">" +
             "<" + dir.toString() + ">" + amount;
    }

    public static final List<DirRefCount> NONE = Collections.emptyList();
  }

  /**
   * Add comment to output code.
   * @param comment
   */
  public void addComment(String comment);

  /**
   * Modify the reference counts of several variables.  A batch is provided
   * to allow backend to optimize if possible.
   * @param refcounts list of reference count operations
   */
  public void modifyRefCounts(List<DirRefCount> refcounts);

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
   * Call a previously defined foreign function.  This will use the version
   * defined by the {@link LocalForeignFunction} implementation.
   * @param outputs
   * @param inputs
   * @param id id of the function
   * @param props
   */
  public void callForeignFunctionLocal(FnID id,
          List<Var> outputs, List<Arg> inputs);

  /**
   * Call a previously defined foreign function.  This will use the version
   * defined by the {@link WrappedForeignFunction} implementation.
   * @param id id of the function
   * @param outputs
   * @param inputs
   * @param props
   */
  public void callForeignFunctionWrapped(FnID id,
      List<Var> outputs, List<Arg> inputs, TaskProps props);

  /**
   * Call an IR function (i.e. one created with startFunction() and endFunction())
   * @param id id of function
   * @param outputs outputs
   * @param inputs inputs
   * @param blockOn which inputs to defer executions of function (only application
   *              to asynchronously executing functions
   * @param mode calling mode for function
   * @param props task properties (if function is asynchronous)
   */
  public void functionCall(FnID id,
      List<Var> outputs, List<Arg> inputs, List<Boolean> blockOn,
      ExecTarget mode, TaskProps props);

  /**
   * Generate command to run an external application immediately
   * @param redirects
   */
  public void runExternal(Arg cmd, List<Arg> args,
           List<Var> outFiles, List<Arg> inFiles,
           Redirects<Arg> redirects,
           boolean hasSideEffects, boolean deterministic);
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
   * Assign a file future object.  Increment local file refcount.
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
  public void retrieveReference(Var dst, Var src, Arg acquireRead,
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

  /**
   * Store a local data value to a compound data type.
   * The exact behaviour should depend on the type of dst, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   */
  public void assignArrayRecursive(Var dst, Arg src);

  /**
   * Store a local data value to a compound data type.
   * The exact behaviour should depend on the type of dst, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   */
  public void assignStructRecursive(Var dst, Arg src);

  /**
   * Store a local data value to a compound data type.
   * The exact behaviour should depend on the type of dst, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   */
  public void assignBagRecursive(Var dst, Arg src);

  /**
   * Retrieve from a compound data type to a local value
   * The exact behaviour should depend on the type of src, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   * @param decr
   */
  public void retrieveArrayRecursive(Var dst, Var src, Arg decr);

  /**
   * Retrieve from a compound data type to a local value
   * The exact behaviour should depend on the type of src, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   * @param decr
   */
  public void retrieveStructRecursive(Var dst, Var src, Arg decr);

  /**
   * Retrieve from a compound data type to a local value
   * The exact behaviour should depend on the type of src, which
   * may or may not have {@link RefType} indirection in it.
   * @param dst
   * @param src
   * @param decr
   */
  public void retrieveBagRecursive(Var dst, Var src, Arg decr);

  /**
   * Unpack a nested array into a local flat array
   * @param flatLocalArray local {@link ArrayType} for output
   * @param inputArray a shared {@link ArrayType}, maybe nested, for input
   */
  public void unpackArrayToFlat(Var flatLocalArray, Arg inputArray);

  /**
   * Wait for and copy the value of the referand of src to dst
   * @param dst destination {@link ScalarFutureType}
   * @param src a {@link RefType} variable
   */
  public void dereferenceScalar(Var dst, Var src);

  /**
   * Wait for and copy the value of the referand of src to dst
   * @param dst destination of {@link FileFutureType}
   * @param src a {@link RefType} variable
   */
  public void dereferenceFile(Var dst, Var src);

  /**
   * Make dst an alias of src.
   * @param dst
   * @param src
   */
  public void makeAlias(Var dst, Var src);

  /**
   * Copy non-closed non-local data once src is closed.
   * @param dst
   * @param src
   */
  public void asyncCopy(Var dst, Var src);

  /**
   * Copy closed non-local data synchronously, assuming src is closed
   * @param dst
   * @param src
   */
  public void syncCopy(Var dst, Var src);

  /**
   * Build a complete local struct value.
   * @param struct a local {@link StructType} to initialize.
   * @param fieldPaths paths to assign, with multiple entries for
   *                   nested structs
   * @param fieldVals values of paths to assign
   */
  public void buildStructLocal(Var struct, List<List<String>> fieldPaths,
                                List<Arg> fieldVals);

  /**
   * Decrement local file refcount, deleting referenced file if needed
   * @param fileVal a {@link FileValueType} file
   */
  public void decrLocalFileRefCount(Var fileVal);

  /**
   * Free local blob value
   * @param blobval a {@link ScalarValueType} blob
   */
  public void freeBlob(Var blobval);

  /**
   * Extract handle to filename future out of file variable
   * @param filename {@link ScalarFutureType} string for output, must be alias
   * @param file {@link FileFutureType} with filename
   */
  public void getFileNameAlias(Var filename, Var file);

  /**
   * Copy filename from future to file
   * @param file {@link FileFutureType} to set filename on
   * @param filename {@link ScalarFutureType} string
   */
  public void copyInFilename(Var file, Var filename);

  /**
   * Extract handle to filename future out of localfile variable
   * @param filename {@link ScalarValueType} string for output
   * @param file {@link FileValueType} with filename
   */
  public void getLocalFileName(Var filename, Var file);

  /**
   * Determine if a file is mapped
   * @param isMapped a {@link ScalarValueType} boolean for output
   * @param file a {@link FileFutureType} variable
   */
  public void isMapped(Var isMapped, Var file);

  /**
   * Choose a temporary file name
   * @param filenameVal a {@link ScalarValueType} of string for output
   */
  public void chooseTmpFilename(Var filenameVal);


  /**
   * Initialise a local file with a filename.
   * @param localFile an uninitialized {@link FileValueType}
   * @param filenameVal a {@link ScalarValueType} string with the filename
   * @param isMapped a {@link ScalarValueType} bool saying whether the file is
   *            mapped - i.e. whether it must be retained in all cases
   */
  public void initLocalOutputFile(Var localFile, Arg filenameVal,
                                  Arg isMapped);
  /**
   * Get filename of file future to a local string value
   * @param filenameVal a {@link ScalarValueType} string for output
   * @param file a {@link FileFutureType}
   */
  public void getFilenameVal(Var filenameVal, Var file);

  /**
   * Set filename of file future to a local string value
   * @param file a {@link FileFutureType} to set filename of
   * @param filenameVal a {@link ScalarValueType} string
   */
  public void setFilenameVal(Var file, Arg filenameVal);

  /**
   * Copy file contents for files represented by local file values
   * @param dst {@link FileValueType} for output initialised with file name
   * @param src a {@link FileValueType} for input initialised with file name
   */
  public void copyFileContents(Var dst, Var src);

  /**
   * Create an alias to a struct field
   * @param dst a variable of time matching the field, of alias type.
   * @param struct a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   */
  public void structCreateAlias(Var dst, Var struct, List<String> fields);

  /**
   * Retrieve the value of a struct field.
   * @param dst output var with retrieved type of field
   * @param struct a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   * @param decr read reference counts to decrement from struct
   */
  public void structRetrieveSub(Var dst, Var struct, List<String> fields,
                                Arg decr);

  /**
   * Asynchronous copy of struct field to another variable.
   * Consumes a read refcount for the struct.
   * @param dst output var with same type as field
   * @param struct a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   */
  public void structCopyOut(Var dst, Var struct, List<String> fields);

  /**
   * Asynchronous copy of struct field to another variable.
   * Consumes a read refcount for the struct.
   * @param dst output var with same type as field
   * @param struct a {@link RefType} to a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   */
  public void structRefCopyOut(Var dst, Var struct, List<String> fields);

  /**
   * Assign variable to struct field
   * @param struct the non-local {@link StructType} to modify
   * @param fields the field path (may refer to a field in a nested struct)
   * @param src var with retrieved type of field
   */
  public void structStore(Var struct, List<String> fields, Arg src);

  /**
   * Copy variable to struct field asynchronously
   * @param struct the non-local {@link StructType} to modify
   * @param fields the field path (may refer to a field in a nested struct)
   * @param src var with same type as field
   */
  public void structCopyIn(Var struct, List<String> fields, Var src);

  /**
   * Assign variable to struct field through a reference to the struct
   * @param structRef a {@link RefType} to a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   * @param src var with retrieved type of field
   */
  public void structRefStoreSub(Var structRef, List<String> fields, Arg src);

  /**
   * Assign variable to struct field through a reference to the struct
   * @param structRef a {@link RefType} to a non-local {@link StructType}
   * @param fields the field path (may refer to a field in a nested struct)
   * @param src var with same type as field
   */
  public void structRefCopyIn(Var structRef, List<String> fields, Var src);

  /**
   * Create a nested datum in struct Array or return the existing
   * datum if it currently exists.
   * @param result alias for the created or retrieved inner datum
   * @param struct outer struct, modified if new datum is created
   * @param key key for outerArray
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void structCreateNested(Var result,
      Var struct, List<String> fields, Arg callerReadRefs, Arg callerWriteRefs,
      Arg readDecr, Arg writeDecr);

  /**
   * Create an alias to an array member
   * @param dst a variable of time matching the member, of alias type
   * @param array a non-local {@link ArrayType}
   * @param key key into array
   */
  public void arrayCreateAlias(Var dst, Var array, Arg key);

  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param dst variable with retrieved type of array member
   * @param array non-local {@link ArrayType}
   * @param key key into array
   * @param decr decrement read refcount of array
   * @param acquire acquire read refcount of referand
   */
  public void arrayRetrieve(Var dst, Var array, Arg key, Arg decr, Arg acquire);

  /**
   * Copy out array member once it is assigned.
   * @param dst variable with same type as array member
   * @param array non-local {@link ArrayType}
   * @param key key into array
   */
  public void arrayCopyOutImm(Var dst, Var array, Arg key);

  /**
   * Copy out array member once it is assigned.
   * @param dst variable with same type as array member
   * @param array non-local {@link ArrayType}
   * @param key future for key
   */
  public void arrayCopyOutFuture(Var dst, Var array, Var key);

  /**
   * Copy out array member once it is assigned.
   * @param dst variable with same type as array member
   * @param array reference to non-local {@link ArrayType}
   * @param key key into array
   */
  public void arrayRefCopyOutImm(Var dst, Var array, Arg key);

  /**
   * Copy out array member once it is assigned.
   * @param dst variable with same type as array member
   * @param array reference to non-local {@link ArrayType}
   * @param key future for key
   */
  public void arrayRefCopyOutFuture(Var dst, Var array, Var key);

  /**
   * Check if array currently contains a key (doesn't wait for close)
   * @param dst {@link ScalarValueType} boolean for output
   * @param array a non-local {@link ArrayType}
   * @param key key for array
   */
  public void arrayContains(Var dst, Var array, Arg key);

  /**
   * Check if local array contains a key
   * @param dst {@link ScalarValueType} boolean for output
   * @param array a local {@link ArrayType}
   * @param key key for array
   */
  public void arrayLocalContains(Var dst, Var array, Arg key);

  /**
   * Lookup current size of contain (don't wait for close)
   * @param dst {@link ScalarValueType} int for output
   * @param container non-local container type e.g {@link ArrayType}
   *                  or {@link BagType}
   */
  public void containerSize(Var dst, Var container);

  /**
   * Lookup current size of contain (don't wait for close)
   * @param dst {@link ScalarValueType} int for output
   * @param container local container type e.g {@link ArrayType}
   *                  or {@link BagType}
   */
  public void containerLocalSize(Var dst, Var container);

  /**
   * Store member to the array slot identified by key.  Executes synchronously.
   * @param array a non-local {@link ArrayType} to modify
   * @param key array key
   * @param member value with retrieved type of array member
   * @param writeDecr number of write refcounts to decrement
   */
  public void arrayStore(Var array, Arg key, Arg member,
                         Arg writeDecr);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set.
   * @param array a non-local {@link ArrayType} to modify
   * @param key future for array key
   * @param member future with type of array member
   * @param writeDecr number of write refcounts to decrement
   */
  public void arrayStoreFuture(Var array, Var key, Arg member, Arg writeDecr);

  /**
   * Copy member to the array slot identified by key.  Executes synchronously.
   * @param array a non-local {@link ArrayType} to modify
   * @param key array key
   * @param member value with retrieved type of array member
   * @param writeDecr number of write refcounts to decrement
   */
  public void arrayCopyInImm(Var array, Arg key, Var member, Arg writeDecr);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set.
   * @param array a non-local {@link ArrayType} to modify
   * @param key future for array key
   * @param member future with type of array member
   * @param writeDecr number of write refcounts to decrement
   */
  public void arrayCopyInFuture(Var array, Var key, Var member, Arg writeDecr);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set. Acquires write refcount from reference.
   * @param array {@link RefType} to a non-local {@link ArrayType} to modify
   * @param key array key
   * @param member retrieved type of array member
   */
  public void arrayRefStoreImm(Var array, Arg key, Arg member);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set. Acquires write refcount from reference.
   * @param array {@link RefType} to a non-local {@link ArrayType} to modify
   * @param key future for array key
   * @param member retrieved type of array member
   */
  public void arrayRefStoreFuture(Var array, Var key, Arg member);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set. Acquires write refcount from reference.
   * @param array {@link RefType} to a non-local {@link ArrayType} to modify
   * @param key value of array key
   * @param member future for array member
   */
  public void arrayRefCopyInImm(Var array, Arg ix, Var member);

  /**
   * Store member to the array slot identified by key.  Executes once key is
   * set. Acquires write refcount from reference.
   * @param array {@link RefType} to a non-local {@link ArrayType} to modify
   * @param key array key
   * @param member future for array member
   */
  public void arrayRefCopyInFuture(Var array, Var ix, Var member);

  /**
   * Build array with specified key-value pairs.
   *
   * Decrements a write refcount from the array.
   *
   * @param array an output non-local {@link ArrayType}
   * @param keys list of key values for build array
   * @param vals list of values with retrieved type of array member
   */
  public void arrayBuild(Var array, List<Arg> keys, List<Arg> vals);

  /**
   * Create a nested datum in outerArray or return the existing
   * datum if it currently exists.
   * @param result alias for the created or retrieved inner array
   * @param outerArray outer array, modified if new datum is created
   * @param key key for outerArray
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void arrayCreateNestedImm(Var result,
      Var outerArray, Arg key, Arg callerReadRefs, Arg callerWriteRefs,
      Arg readDecr, Arg writeDecr);

  /**
   * Create a nested datum in outerArray or return the existing
   * datum if it currently exists.  Executes asynchronously and
   * uses output reference.
   * @param result reference that is set to inner datum
   * @param outerArray outer array, modified if new datum is created
   * @param key key future for outerArray
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void arrayCreateNestedFuture(Var result, Var outerArray, Var key);

  /**
   * Create a nested datum in outerArray or return the existing
   * datum if it currently exists.  Executes asynchronously and
   * uses output reference.
   * @param result reference that is set to inner datum
   * @param outerArray writable reference to outer array, modified if new datum is created
   * @param key key for outerArray
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void arrayRefCreateNestedImm(Var result, Var array, Arg ix);

  /**
   * Create a nested datum in outerArray or return the existing
   * datum if it currently exists.  Executes asynchronously and
   * uses output reference.
   * @param result reference that is set to inner datum
   * @param outerArray writable reference to outer array, modified if new datum is created
   * @param key key future for outerArray
   * @param callerReadRefs number of refcounts to give back to caller
   * @param callerWriteRefs number of refcounts to give back to caller
   * @param readDecr decrement array
   * @param writeDecr decrement array
   */
  public void arrayRefCreateNestedFuture(Var result, Var array, Var ix);

  /**
   * Insert a value into a bag
   * @param bag a non-local {@link BagType}
   * @param value a value with the retrieved type of the bag member
   * @param writeDecr write reference counts to decrement from bag
   */
  public void bagInsert(Var bag, Arg value, Arg writeDecr);;

  /**
   * Initialize an updateable variable with an initial value
   * @param updateable a {@link ScalarUpdateableType} variable
   * @param val a {@link ScalarValueType} variable
   */
  public void initScalarUpdateable(Var updateable, Arg val);

  /**
   * Get the latest value of an updateable variable
   * @param result a {@link ScalarValueType} variable for output
   * @param updateable a {@link ScalarFutureType} variable
   */
  public void latestValue(Var result, Var updateable);

  /**
   * Update a scalar updateable variable once value of val is available
   * @param updateable a {@link ScalarUpdateableType} variable
   * @param updateMode the mode of updating
   * @param val a {@link ScalarFutureType} variable for the value
   */
  public void updateScalarFuture(Var updateable,
      Operators.UpdateMode updateMode, Var val);

  /**
   * Update a scalar updateable variable immediately
   * @param updateable a {@link ScalarUpdateableType} variable
   * @param updateMode the mode of updating
   * @param val a {@link ScalarValueType} variable for the value
   */
  public void updateScalarImm(Var updateable,
      Operators.UpdateMode updateMode, Arg val);

  /**
   * Whether retrieving checkpoints is enabled
   * @param out variable with {@link ScalarValueType} bool
   */
  public void checkpointLookupEnabled(Var out);

  /**
   * Whether writing checkpoints is enabled
   * @param out variable with {@link ScalarValueType} bool
   */
  public void checkpointWriteEnabled(Var out);

  /**
   * Write an encoded checkpoint out
   * @param key a {@link ScalarValueType} of blob
   * @param val a {@link ScalarValueType} of blob
   */
  public void writeCheckpoint(Arg key, Arg val);

  /**
   * Lookup an encoded checkpoint,
   * @param checkpointExists a {@link ScalarValueType} of bool for output,
   *            whether the checkpoint exists
   * @param val a {@link ScalarValueType} of blob for output, only set if
   *            the checkpoint exists
   * @param key a {@link ScalarValueType} of blob
   */
  public void lookupCheckpoint(Var checkpointExists, Var val, Arg key);

  /**
   * @param packed a {@link ScalarValueType} of blob for output
   * @param unpacked local value variables for packing
   */
  public void packValues(Var packed, List<Arg> unpacked);

  /**
   * Unpack data packed using packValues
   * @param unpacked a list of local value variables for output,
   *        matching input to packValues
   * @param packed a packed {@link ScalarValueType} of blob
   */
  public void unpackValues(List<Var> unpacked, Arg packed);

}