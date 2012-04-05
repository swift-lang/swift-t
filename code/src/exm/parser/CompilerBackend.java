package exm.parser;

import java.util.List;

import exm.ast.Types.FunctionType;
import exm.ast.Types.SwiftType;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.ast.*;
import exm.ast.Builtins.ArithOpcode;
import exm.ast.Builtins.UpdateMode;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.util.UndefinedTypeException;
import exm.parser.util.UserException;

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
      VariableStorage storage, DefType defType, String mapping) 
           throws UndefinedTypeException;

  public abstract void closeArray(Variable arr);

  public abstract void localArithOp(ArithOpcode op, Variable out, 
                                            List<Oparg> in);
  
  /**
   * Set target=addressof(src)
   */
  public abstract void assignReference(Variable target, Variable src);

  public abstract void dereferenceInt(Variable target, Variable src);
  
  public abstract void dereferenceBool(Variable target, Variable src);

  public abstract void dereferenceFloat(Variable dst, Variable src);
  
  public abstract void dereferenceBlob(Variable dst, Variable src);

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
  public abstract void assignInt(Variable target, Oparg src);
  public abstract void retrieveInt(Variable target, Variable source);

  public abstract void assignFloat(Variable target, Oparg src);
  public abstract void retrieveFloat(Variable target, Variable source);

  /** assignString, which can take a value variable or a literal int in oparg
   */
  public abstract void assignString(Variable target, Oparg src);

  public abstract void retrieveString(Variable target, Variable source);
  
  public abstract void assignBool(Variable target, Oparg src);
  public abstract void retrieveBool(Variable target, Variable source);

  public abstract void appFunctionCall(String function, List<Variable> inputs,
      List<Variable> outputs, Oparg priority);

  /**
   * NOTE: all built-ins should be defined before composites
   * @param function
   * @param inputs
   * @param outputs
   * @param priorityVal 
   */
  public abstract void builtinFunctionCall(String function,
      List<Variable> inputs, List<Variable> outputs, Oparg priority);

  public abstract void compositeFunctionCall(String function,
      List<Variable> inputs, List<Variable> outputs, List<Boolean> blockOn, 
      boolean async, Oparg priority);

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

  public abstract void arrayLoadComputedIndex(Variable oVar, Variable arrayVar,
      Variable indexVar, boolean isArrayRef);

  public abstract void arrayLoadImmediateIx(Variable oVar, Variable arrayVar,
      Oparg arrayIndex, boolean isArrayRef);
  
  /**
   * Direct lookup of array without any blocking at all.  This is only
   * safe to use if we know the array is closed, or if we know that the
   * item at this index is already there
   * @param oVar
   * @param arrayVar
   * @param arrayIndex
   */
  public abstract void arrayLoadImmediate(Variable oVar, Variable arrayVar,
      Oparg arrayIndex);

  public abstract void arrayStoreComputedIndex(Variable iVar,
      Variable arrayVar, Variable indexVar);
  
  public abstract void arrayRefStoreComputedIndex(Variable iVar,
      Variable arrayVar, Variable indexVar, Variable outerArrayVar);

  public abstract void arrayStoreImmediate(Variable iVar, Variable arrayVar,
      Oparg arrayIndex);
  
  public abstract void arrayRefStoreImmediateIx(Variable iVar, 
      Variable arrayVar, Oparg arrayIndex, Variable outerArrayVar);

  public abstract void arrayCreateNestedComputedIndex(Variable arrayResult,
      Variable arrayVar, Variable indexVar);

  public abstract void arrayCreateNestedImmediate(Variable arrayResult,
      Variable arrayVar, Oparg arrIx);

  public abstract void arrayRefCreateNestedComputedIndex(Variable arrayResult,
      Variable arrayVar, Variable indexVar);

  public abstract void arrayRefCreateNestedImmediateIx(Variable arrayResult,
      Variable arrayVar, Oparg arrIx);

  public abstract void initUpdateable(Variable updateable, Oparg val);
  public abstract void latestValue(Variable result, Variable updateable);
  
  public abstract void update(Variable updateable, UpdateMode updateMode,
                              Variable val);
  /** Same as above, but takes a value or constant as arg */
  public abstract void updateImm(Variable updateable, UpdateMode updateMode,
      Oparg val);
  
  public abstract void defineBuiltinFunction(String name,
                String pkg, String version, String symbol,
                FunctionType type) throws UserException;

  public abstract void startCompositeFunction(String functionName,
      List<Variable> oList, List<Variable> iList, boolean async)
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
  public abstract void startIfStatement(Variable condition,
      boolean hasElse);

  public abstract void startElseBlock();

  public abstract void endIfStatement();

  /**
   * 
   * @param switchVar must be integer value type
   * @param caseLabels
   * @param hasDefault
   */
  public abstract void startSwitch(Variable switchVar,
      List<Integer> caseLabels, boolean hasDefault);

  public abstract void endCase();

  public abstract void endSwitch();

  /**
   * 
   * @param arrayVar
   * @param memberVar
   * @param loopCountVar counter variable, can be null
   * @param isSync if true, don't spawn off tasks to run iterations asynchronously
   * @param arrayClosed if true, assume array is already closed
   * @param usedVariables
   * @param containersToRegister
   */
  public abstract void startForeachLoop(Variable arrayVar,
      Variable memberVar, Variable loopCountVar, boolean isSync,
      boolean arrayClosed,
      List<Variable> usedVariables, List<Variable> containersToRegister);

  public abstract void endForeachLoop(boolean isSync, boolean arrayClosed,
                                    List<Variable> containersToRegister);

  
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
   * @param containersToRegister containers written in loop body
   * @param desiredUnroll the suggested unrolling factor
   * @param splitDegree the desired loop split factor (negative if no splitting)
   */
  public abstract void startRangeLoop(String loopName, Variable loopVar, 
      Oparg start, Oparg end, Oparg increment, 
      boolean isSync, List<Variable> usedVariables, 
      List<Variable> containersToRegister, int desiredUnroll, int splitDegree);
  public abstract void endRangeLoop(boolean isSync, 
                                    List<Variable> containersToRegister,
                                    int splitDegree);
  /**
   * Add a global variable (currently constant literals are supported)  
   * @param name
   * @param val
   */
  public abstract void addGlobal(String name, Oparg val);
   
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
   * @param containersToRegister any containers that might be written in this block
   * @param explicit true if this is a semantically meaningful wait statement,
   *            false if it can be removed safely without altering semantics.
   *            If true the wait statement will only be optimised out if it
   *            can be shown that the variables are already closed when the
   *            wait is encountered
   */
  public abstract void startWaitStatement(String procName,
      List<Variable> waitVars,
      List<Variable> usedVariables, List<Variable> containersToRegister,
      boolean explicit);

  public abstract void endWaitStatement(List<Variable> containersToRegister);

  
  /**
   * 
   * @param loopName
   * @param loopVars first one is loop condition
   * @param initVals initial values for loop variables
   * @param usedVariables
   * @param containersToRegister
   * @param blockingLoopVars
   */
  public abstract void startLoop(String loopName, List<Variable> loopVars,
      List<Variable> initVals, List<Variable> usedVariables,
      List<Variable> containersToRegister, List<Boolean> blockingVars);
  
  public abstract void loopContinue(List<Variable> newVals,
      List<Variable> usedVariables, List<Variable> registeredContainers,
      List<Boolean> blockingVars);
  public abstract void loopBreak(List<Variable> containersToClose);
  public abstract void endLoop();

}