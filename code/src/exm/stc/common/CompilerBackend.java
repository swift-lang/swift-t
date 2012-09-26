package exm.stc.common;

import java.util.List;

import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.FunctionSemantics.TclOpTemplate;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Variable.DefType;
import exm.stc.common.lang.Variable.VariableStorage;

public interface CompilerBackend {

  public abstract void header();

  public abstract void turbineStartup();

  /**
   * 
   * @param t
   * @param name
   * @param storage
   * @param defType
   * @param mapping null if no mapping
   * @throws UndefinedTypeException
   */
  public abstract void declare(SwiftType t, String name,
      VariableStorage storage, DefType defType, Variable mapping) 
           throws UndefinedTypeException;

  public abstract void closeArray(Variable arr);

  public abstract void localOp(BuiltinOpcode op, Variable out, 
                                            List<Arg> in);
  
  public abstract void asyncOp(BuiltinOpcode op, Variable out, 
                               List<Arg> in, Arg priority);  
  
  /**
   * Set target=addressof(src)
   */
  public abstract void assignReference(Variable target, Variable src);

  public abstract void dereferenceInt(Variable target, Variable src);
  
  public abstract void dereferenceBool(Variable target, Variable src);

  public abstract void dereferenceFloat(Variable dst, Variable src);
  
  public abstract void dereferenceBlob(Variable dst, Variable src);
  
  public abstract void dereferenceFile(Variable dst, Variable src);

  public abstract void retrieveRef(Variable target, Variable src);
  
  /**
   * Copy the handle to a future, creating an alias
   * @param dst
   * @param src
   */
  public abstract void makeAlias(Variable dst, Variable src);

  public abstract void dereferenceString (Variable target, Variable src);

  /**assignInt, which can take a value variable or a literal int in oparg
   */
  public abstract void assignInt(Variable target, Arg src);
  public abstract void retrieveInt(Variable target, Variable source);

  public abstract void assignFloat(Variable target, Arg src);
  public abstract void retrieveFloat(Variable target, Variable source);

  /** assignString, which can take a value variable or a literal int in oparg
   */
  public abstract void assignString(Variable target, Arg src);

  public abstract void retrieveString(Variable target, Variable source);
  
  public abstract void assignBool(Variable target, Arg src);
  public abstract void retrieveBool(Variable target, Variable source);

  public abstract void appFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, Arg priority);

  /**
   * NOTE: all built-ins should be defined before composites
   * @param function
   * @param inputs
   * @param outputs
   * @param priorityVal 
   */
  public abstract void builtinFunctionCall(String function,
      List<Variable> inputs, List<Variable> outputs, Arg priority);

  public abstract void compositeFunctionCall(String function,
      List<Variable> inputs, List<Variable> outputs, List<Boolean> blockOn, 
      TaskMode mode, Arg priority);

  public abstract void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Variable> outputs);
  
  /**
   * lookup structVarName.structField and copy to oVarName
   * @param structVarName
   * @param structField
   * @param oVarName
   */
  public abstract void structLookup(Variable structVar, String structField,
      Variable result);
  
  public abstract void structRefLookup(Variable structVar, String fieldName,
      Variable tmp);

  public abstract void structClose(Variable struct);

  public abstract void structInsert(Variable structVar, String fieldName,
                                          Variable fieldContents);

  public abstract void arrayLookupFuture(Variable oVar, Variable arrayVar,
      Variable indexVar, boolean isArrayRef);

  public abstract void arrayLookupRefImm(Variable oVar, Variable arrayVar,
      Arg arrayIndex, boolean isArrayRef);
  
  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param oVar
   * @param arrayVar
   * @param arrayIndex
   */
  public abstract void arrayLookupImm(Variable oVar, Variable arrayVar,
      Arg arrayIndex);

  public abstract void arrayInsertFuture(Variable iVar,
      Variable arrayVar, Variable indexVar);
  
  public abstract void arrayRefInsertFuture(Variable iVar,
      Variable arrayVar, Variable indexVar, Variable outerArrayVar);

  public abstract void arrayInsertImm(Variable iVar, Variable arrayVar,
      Arg arrayIndex);
  
  public abstract void arrayRefInsertImm(Variable iVar, 
      Variable arrayVar, Arg arrayIndex, Variable outerArrayVar);

  public abstract void arrayCreateNestedFuture(Variable arrayResult,
      Variable arrayVar, Variable indexVar);

  public abstract void arrayCreateNestedImm(Variable arrayResult,
      Variable arrayVar, Arg arrIx);

  public abstract void arrayRefCreateNestedFuture(Variable arrayResult,
      Variable arrayVar, Variable indexVar);

  public abstract void arrayRefCreateNestedImm(Variable arrayResult,
      Variable arrayVar, Arg arrIx);

  public abstract void initUpdateable(Variable updateable, Arg val);
  public abstract void latestValue(Variable result, Variable updateable);
  
  public abstract void update(Variable updateable, Operators.UpdateMode updateMode,
                              Variable val);
  /** Same as above, but takes a value or constant as arg */
  public abstract void updateImm(Variable updateable, Operators.UpdateMode updateMode,
      Arg val);
  
  public abstract void defineBuiltinFunction(String name,
                String pkg, String version, String symbol,
                FunctionType type, TclOpTemplate inlineTclTemplate) 
                    throws UserException;

  public abstract void startCompositeFunction(String functionName,
      List<Variable> oList, List<Variable> iList, TaskMode mode)
            throws UserException;

  public abstract void endCompositeFunction();

  public abstract void startNestedBlock();

  public abstract void endNestedBlock();

  public abstract void addComment(String comment);

  /** NOT UPDATED */

  public abstract void defineApp(String functionName, List<Variable> iList,
      List<Variable> oList, String body);

  /**
   * @param condition the variable name to branch based on (int or bool value type)
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
  public abstract void startIfStatement(Arg condition,
      boolean hasElse);

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
   * @param arrayVar
   * @param memberVar
   * @param loopCountVar counter variable, can be null
   * @param isSync if true, don't spawn off tasks to run iterations asynchronously
   * @param splitDegree
   * @param arrayClosed if true, assume array is already closed
   * @param usedVariables
   * @param keepOpenVars
   */
  public abstract void startForeachLoop(Variable arrayVar,
      Variable memberVar, Variable loopCountVar, boolean isSync,
      int splitDegree, boolean arrayClosed,
      List<Variable> usedVariables, List<Variable> keepOpenVars);

  public abstract void endForeachLoop(boolean isSync, int splitDegree, 
            boolean arrayClosed, List<Variable> keepOpenVars);

  
  /**
   * A loop over a prespecified range.  The range can be totally fixed
   *   ( the bounds might be literal integers) or can vary at runtime (
   *   ( in which case it is specified by integer value variables )
   *   The loop construct should run immediately, but have the loop iterations
   *   run in parallel
   * @param loopName unique name for loop
   * @param loopVar variable (integer value) used to store iteration number 
   * @param start start (inclusive) of the loop: should be int or int value var
   * @param end end (inclusive) of the loop: should be int or int value var
   * @param increment increment of the loop: should be int or int value var
   * @param isSync if true, don't spawn a task per iteration: run loop   
   *            body synchronously
   * @param usedVariables variables used in loop body
   * @param keepOpenVars vars to keep open for assignment in loop body
   * @param desiredUnroll the suggested unrolling factor
   * @param splitDegree the desired loop split factor (negative if no splitting)
   */
  public abstract void startRangeLoop(String loopName, Variable loopVar, 
      Arg start, Arg end, Arg increment, 
      boolean isSync, List<Variable> usedVariables, 
      List<Variable> keepOpenVars, int desiredUnroll, int splitDegree);
  public abstract void endRangeLoop(boolean isSync, 
                                    List<Variable> keepOpenVars,
                                    int splitDegree);
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

  public abstract void optimise() throws UserException;

  public abstract void regenerate(CompilerBackend codeGen) throws UserException;

  /**
   *
   * @param procName the name of the wait block (useful so generated
   *    tcl code can have a nice name for the block)
   * @param waitVars
   * @param usedVariables any variables which are read or written inside block
   * @param keepOpenVars any vars that need to be kept open for wait
   * @param explicit true if this is a semantically meaningful wait statement,
   *            false if it can be removed safely without altering semantics.
   *            If true the wait statement will only be optimised out if it
   *            can be shown that the variables are already closed when the
   *            wait is encountered
   */
  public abstract void startWaitStatement(String procName,
      List<Variable> waitVars,
      List<Variable> usedVariables, List<Variable> keepOpenVars,
      boolean explicit);

  public abstract void endWaitStatement(List<Variable> keepOpenVars);

  
  /**
   * 
   * @param loopName
   * @param loopVars first one is loop condition
   * @param initVals initial values for loop variables
   * @param usedVariables
   * @param keepOpenVars
   * @param blockingLoopVars
   */
  public abstract void startLoop(String loopName, List<Variable> loopVars,
      List<Variable> initVals, List<Variable> usedVariables,
      List<Variable> keepOpenVars, List<Boolean> blockingVars);
  
  public abstract void loopContinue(List<Variable> newVals,
      List<Variable> usedVariables, List<Variable> keepOpenVars,
      List<Boolean> blockingVars);
  public abstract void loopBreak(List<Variable> varsToClose);
  public abstract void endLoop();

}