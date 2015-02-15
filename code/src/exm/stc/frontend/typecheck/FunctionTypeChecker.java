package exm.stc.frontend.typecheck;

import static exm.stc.frontend.typecheck.TypeChecker.findExprType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.InvalidConstructException;
import exm.stc.common.exceptions.InvalidOverloadException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.TupleType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.frontend.Context;
import exm.stc.frontend.Context.FnOverload;
import exm.stc.frontend.LogHelper;
import exm.stc.frontend.tree.FunctionCall;

/**
 * Typechecking logic for functions.
 *
 * @author tim
 *
 */
public class FunctionTypeChecker {

  public static Type findFuncCallExprType(Context context, SwiftAST tree)
      throws UndefinedFunctionException, UserException {
    FunctionCall f = FunctionCall.fromAST(context, tree, false);

    return exprTypeFromMatch(concretiseInputs(context, f));
  }

  public static ConcreteMatch concretiseFunctionCall(
          Context context, FunctionCall fc, List<Var> outputs)
          throws UserException {
    FnMatch match = concretiseInputs(context, fc);

    List<Type> outTs = Var.extractTypes(outputs);
    FunctionType concreteOutTs = concretiseOutputs(context, match, outputs, outTs);

    return new ConcreteMatch(match.overload, concreteOutTs);
  }

  /**
   * Concrete input type for function
   * @throws TypeMismatchException
   */
  public static Pair<Type, Type>
  concretiseFnArg(Context context, String function, String argName,
      Type formalArgT, Type argExprT) throws TypeMismatchException {
    Pair<Type, Type> res = selectArgType(formalArgT, argExprT, true);
    if (res == null) {
      throw argumentTypeException(context, argName, formalArgT, argExprT,
          " in call to function " + function);
    }

    assert(res.val1.isConcrete()) : "Non-concrete arg type: " + res.val1;
    assert(res.val2.isConcrete()) : "Non-concrete arg type: " + res.val2;
    return res;
  }

  /**
   * Select alternative type out of type union (if it's a union).
   *
   * Handles cases where both function formal argument type and expression type
   * are union types.  In case of multiple possibilities prefers picking first
   * alternative in expression type, followed by first formal argument alternative
   *
   * @param formalArgT formal argument type
   * @param argExprT type of the argument expression
   * @param concretiseAll if true, concretise all types, to void
   *      if no constraints.  If false, doesn't fill in type vars
   *      or wildcards.
   * @return (selected type of argument, selected type of variable)
   */
  public static Pair<Type, Type>
  selectArgType(Type formalArgT, Type argExprT, boolean concretiseAll) {
    assert(!formalArgT.hasTypeVar()) : formalArgT + " " + argExprT;
    for (Type argExprAlt: UnionType.getAlternatives(argExprT)) {
      for (Type formalArgAlt: UnionType.getAlternatives(formalArgT)) {

        Type argExprAltNoRef;
        if (Types.isRef(argExprAlt)) {
          argExprAltNoRef = argExprAlt.memberType();
        } else {
          argExprAltNoRef = argExprAlt;
        }


        if (concretiseAll) {
          /* At this stage, any formalArg typevars constrained by argument
           * type should be bound.  We need to bind any unbound typevars in
           * the expression type.
           */
          Map<String, Type> tvBindings;
          tvBindings = argExprAltNoRef.matchTypeVars(formalArgAlt);
          if (tvBindings == null) {
            continue;
          } else {
            argExprAltNoRef = argExprAltNoRef.bindTypeVars(tvBindings);
          }
        }

        if (argExprAltNoRef.assignableTo(formalArgAlt)) {
          Type argExprResult = argExprAltNoRef.concretize(formalArgAlt);
          if (Types.isRef(argExprAlt)) {
            argExprResult = new RefType(argExprResult,
                ((RefType)argExprAlt).mutable());
          }
          return Pair.create(formalArgAlt, argExprResult);
        }
      }
    }
    return null; // if no alternatives
  }

  /**
   * Narrow possible function call types based on inputs.
   * @param context
   * @param fc
   * @param resolveOverload if true, resolve to a single overload
   * @return list of possible concrete function types with varargs, typevars
   *          and union type args removed.  Grouped by which overload they
   *          match.  Each match should have at least one concrete function
   *          type associated with it.
   * @throws UserException
   */
  private static FnMatch concretiseInputs(Context context, FunctionCall fc)
      throws UserException {
    List<Type> posArgTypes = new ArrayList<Type>(fc.posArgs().size());
    Map<String, Type> kwArgTypes = new HashMap<String, Type>();
    for (SwiftAST arg: fc.posArgs()) {
      posArgTypes.add(findExprType(context, arg));
    }
    for (Entry<String, SwiftAST> kwArg: fc.kwArgs().entrySet()) {
      kwArgTypes.put(kwArg.getKey(), findExprType(context, kwArg.getValue()));
    }

    FnCallInfo info = new FnCallInfo(fc.originalName(), fc.overloads(),
                                     posArgTypes, kwArgTypes);

    return concretiseInputsOverloaded(context, info);
  }

  /**
   * Resolve overloaded function call to a single overload based on input
   * arguments (output arguments are *not* used to resolve overloads).
   * @param context
   * @param fc
   * @return
   * @throws TypeMismatchException
   */
  static FnMatch concretiseInputsOverloaded(Context context,
      FnCallInfo fc) throws TypeMismatchException {
    assert(fc.fnTypes.size() >= 1);
    boolean overloaded = fc.fnTypes.size() >= 2;

    List<FnMatch> matches = new ArrayList<FnMatch>();

    for (FnOverload fnType: fc.fnTypes) {
      FnMatch match = concretiseInputsNonOverloaded(context, fnType,
                            fc.argTypes, fc.kwArgTypes, !overloaded);

      if (match != null) {
        matches.add(match);
      }
    }

    if (matches.size() == 0) {
      throw overloadMatchFailException(context, fc,
            "did not match any overload of function");
    }

    if (matches.size() >= 2) {
      // In some cases we may be able to resolve ambiguity
      return tieBreakMatchingOverloads(context, fc, matches);
    }

    return matches.get(0);
  }

  /**
   * Apply tie-breaking rules to choose overload, throw exception
   * if still ambiguous
   * @param context
   * @param matches
   * @param argTypes
   * @return
   * @throws TypeMismatchException if no way to consistenly resolve
   */
  private static FnMatch tieBreakMatchingOverloads(Context context,
      FnCallInfo fc, List<FnMatch> matches)
          throws TypeMismatchException {

    /*
     * One entry per candidate match.
     * Track whether one is preferred over another
     */
    boolean preferred[] = new boolean[matches.size()];
    boolean nonPreferred[] = new boolean[matches.size()];

    for (int i = 0; i < fc.argTypes.size(); i++) {
      updateTieBreakPreferredForArg(matches, fc.argTypes, i, preferred,
                                    nonPreferred);
    }

    for (int m = 0; m < matches.size(); m++) {
      if (preferred[m] && !nonPreferred[m]) {
        return matches.get(m);
      }
    }

    throw overloadMatchFailException(context, fc,
        "could not unambiguously resolve overload");
  }

  private static void updateTieBreakPreferredForArg(List<FnMatch> matches,
      List<Type> argTypes, int argPos, boolean[] preferred,
      boolean[] nonPreferred) {
    Type argType = argTypes.get(argPos);
    List<Type> argTypeAlts = UnionType.getAlternatives(argType);
    if (argTypeAlts.size() >= 2) {
      /*
       * See if using first element of union helps choose a type.
       * E.g. if an argument is an int literal 5 that can be interpreted as
       * an int or a float, see if forcing it to be an int resolves things.
       */
      Type firstArgType = argTypeAlts.get(0);
      boolean firstMatches[] = new boolean[matches.size()];
      int firstMatchCount = 0;

      for (int m = 0; m < matches.size(); m++) {
        FnMatch match = matches.get(m);
        Type matchArgType = match.overload.type.getInputs().get(argPos);

        if (firstArgType.assignableTo(matchArgType)) {
          firstMatches[m] = true;
          firstMatchCount++;
        }
      }

      if (firstMatchCount > 0 && firstMatchCount < matches.size()) {
        // This can discriminate between matches
        for (int m = 0; m < matches.size(); m++) {
          if (firstMatches[m]) {
            preferred[m] = true;
          } else {
            nonPreferred[m] = true;
          }
        }
      }
    }
  }

  static FnMatch concretiseInputsNonOverloaded(Context context,
      FnOverload overload, List<Type> argTypes, Map<String, Type> kwArgTypes,
      boolean throwOnFail) throws TypeMismatchException {
    List<MatchedArg> matchedArgs = matchArgs(context, overload, argTypes,
                                              kwArgTypes, throwOnFail);
    if (matchedArgs == null) {
      return null;
    }

    assert(matchedArgs.size() == overload.inArgNames.size() ||
          (overload.type.hasVarargs() &&
              matchedArgs.size() >= overload.inArgNames.size() - 1)) :
                overload.inArgNames + " " + matchedArgs;

    /*
     *  Narrow down possible bindings - choose union types and find possible
     *  typevar bindings.
     */
    List<Type> concreteArgTypes = new ArrayList<Type>(argTypes.size());
    MultiMap<String, Type> tvConstraints = new MultiMap<String, Type>();
    for (int i = 0; i < matchedArgs.size(); i++) {
      MatchedArg matchedArg = matchedArgs.get(i);
      Type exp = matchedArg.formalArgType;
      Type act = matchedArg.argExprType;

      // a more specific type than expected
      Type exp2;
      if (act != null) {
        exp2 = narrowArgType(context, overload.id, matchedArg.name, exp, act,
                                  tvConstraints, throwOnFail);
        if (exp2 == null) {
          assert(!throwOnFail);
          return null;
        }
      } else {
        // Will be filled with default val
        exp2 = overload.defaultVals.defaultVals().get(i).type();
        assert(exp2.assignableTo(exp));
      }
      concreteArgTypes.add(exp2);
    }
    LogHelper.trace(context, "Call " + overload.id.uniqueName()
        + " specificInputs: " + concreteArgTypes
        + " possible bindings: " + tvConstraints);

    // Narrow down type variable bindings depending on constraints
    Map<String, List<Type>> bindings = unifyTypeVarConstraints(context,
        overload.id, overload.type.getTypeVars(), tvConstraints, throwOnFail);

    LogHelper.trace(context, "Call " + overload.id.uniqueName()
        + " unified bindings: " + tvConstraints);

    List<FunctionType> possibilities = findPossibleFunctionTypes(context,
                       overload.id, overload.type, concreteArgTypes, bindings);

    LogHelper.trace(context, "Call " + overload.id.uniqueName()
                + " possible concrete types: " + possibilities);

    if (possibilities.size() == 0) {
      if (throwOnFail) {
        throw new TypeMismatchException(context, "Arguments for call to "
          + "function " + overload.id.originalName() + " "
          + "were incompatible with function type. "
          + "Function input types were: " + overload.type.getInputs() + ", "
          + "argument types were " + argTypes);
      }
      return null;
    }

    return new FnMatch(overload, possibilities);
  }

  private static FunctionType concretiseOutputs(Context context,
      FnMatch fn, List<Var> outputs, List<Type> outTs)
      throws TypeMismatchException {
    assert(fn.concreteAlts.size() > 0);
    for (FunctionType alt: fn.concreteAlts) {
      assert(alt.getOutputs().size() == outputs.size());

      MultiMap<String, Type> tvConstraints = new MultiMap<String, Type>();

      boolean outputsMatch = true;
      for (int i = 0; i < outTs.size(); i++) {
        Type outT = outTs.get(i);
        Type outArgT = alt.getOutputs().get(i);

        if (outArgT.isConcrete()) {
          if (!outArgT.assignableTo(outT)) {
            outputsMatch = false;
            break;
          }
        } else {
          Map<String, Type> bindings = outArgT.matchTypeVars(outT);
          if (bindings == null) {
            outputsMatch = false;
            break;
          }

          tvConstraints.putAll(bindings);
          LogHelper.trace(context, "Bind " + bindings + " for " +
              outT + " <- " + alt.getOutputs());
        }
      }

      if (!tvConstraints.isEmpty()) {
        // Need to check if we can match types
        Map<String, List<Type>> bindings = unifyTypeVarConstraints(context,
            fn.overload.id, fn.overload.type.getTypeVars(), tvConstraints,
            true);

        Map<String, Type> chosenBindings = new HashMap<String, Type>();
        for (Entry<String, List<Type>> e: bindings.entrySet()) {
          assert(e.getValue().size() >= 1);
          // Choose first
          chosenBindings.put(e.getKey(), e.getValue().get(0));
        }
        alt = (FunctionType)alt.bindTypeVars(chosenBindings);
      }

      // Bind any free types
      alt = (FunctionType)alt.bindAllTypeVars(Types.F_VOID);

      LogHelper.trace(context, "Call " + fn.overload.id + " alternative "
          + "function type " + alt + " match: " + outputsMatch);

      // Choose first viable alternative
      if (outputsMatch) {
        checkFunctionOutputsValid(context, fn.overload.id, outputs);
        return alt;
      }
    }

    throw new TypeMismatchException(context, "Could not find consistent " +
        "binding for type variables.  Viable function signatures based on " +
        "input arguments were: " + fn + " but output types were " + outTs);
  }

  private static void checkFunctionOutputsValid(Context context,
      FnID id, List<Var> outputs) throws TypeMismatchException {
    for (Var output: outputs) {
      checkFunctionOutputValid(context, id, output);
    }
  }

  /**
   * Check that function output var is valid
   * @param output
   * @throws TypeMismatchException
   */
  private static void checkFunctionOutputValid(Context context,
      FnID id, Var output) throws TypeMismatchException {
    if (Types.isFile(output) && output.isMapped() == Ternary.FALSE &&
        !output.type().fileKind().supportsTmpImmediate() &&
        !context.getForeignFunctions().canInitOutputMapping(id)) {
      /*
       * We can't create temporary files for this type.  If we detect any
       * where a definitely unmapped var is an output to a function, then
       * we can avoid generating code where this happens.  Why?
       * Suppose that write a program where a leaf function is called with
       * an unmapped output file.  There are two cases:
       * 1. The unmapped input file is declared in the same scope as
       *    the leaf function => isMapped() == false and we're done.
       * 2. The unmapped input file is a function output argument =>
       *    backtrack through the caller hierarchy until we reach the
       *    function where the unmapped variable was declared.  In that
       *    function we called a composite function with the (definitely)
       *    unmapped variable for which isMapped() == false.
       * So by this simple check we can prevent leaf functions being called
       * with unmapped output files.
       * The exception to this is a special function that will initialize an
       * unmapped variable on its own
       */
      throw new TypeMismatchException(context, "Type " + output.type().typeName()
          + " does not support creating temporary files. Unmapped var " + output.name()
          + " cannot be used as function output variable.");
    }
  }

  /**
   * Narrow down the possible argument types for a function call
   * @param context
   * @param id
   * @param argName name of argument
   * @param formalArgT Formal argument type from abstract function type
   * @param argExprT Type of argument expression for function
   * @param tvConstrains Filled in with constraints for each type variable
   * @param throwOnFail throw TypeMismatchException on failed match,
   *                    otherwise return null
   * @return
   * @throws TypeMismatchException
   */
  private static Type narrowArgType(Context context, FnID id, String argName,
      Type formalArgT, Type argExprT,
      MultiMap<String, Type> tvConstrains, boolean throwOnFail)
          throws TypeMismatchException {
    if (formalArgT.hasTypeVar()) {
      return checkFunArgTV(context, id, argName, formalArgT, argExprT,
                           tvConstrains, throwOnFail);
    } else {
      return checkFunArg(context, id, argName, formalArgT, argExprT, throwOnFail);
    }
  }


  /**
   * Check function input argument type
   * Returns a tuple indicating which formal argument type is selected and
   * what type the input argument expression should be interpreted as having.
   * Does not handle type variables
   * @param context
   * @param id
   * @param argName
   * @param formalArgT
   * @param argExprT
   * @param throwOnFail if true, throw exception on fail, otherwise return null
   * @return selected formal argument type or null
   * @throws TypeMismatchException
   */
  private static Type checkFunArg(Context context,
      FnID id, String argName, Type formalArgT, Type argExprT,
      boolean throwOnFail) throws TypeMismatchException {
    assert(!formalArgT.hasTypeVar()) : formalArgT;

    Pair<Type, Type> alt = selectArgType(formalArgT, argExprT, false);

    if (alt == null) {
      if (throwOnFail) {
        throw argumentTypeException(context, argName, formalArgT, argExprT,
          " in call to function " + id.originalName());
      }
      return null;
    }

    Type res = alt.val1;
    assert(res.isConcrete()) : "Non-concrete arg type: " + res;
    return res;
  }

  /**
   * Check function argument type
   * Returns which formal argument type is selected.
   * Only handles case where formalArgT has a type variable
   * @param context
   * @param id
   * @param argName
   * @param formalArgT
   * @param argExprT
   * @param tvConstraints fill in constraints for type variables
   * @param throwOnFail
   * @return
   * @throws TypeMismatchException
   */
  private static Type checkFunArgTV(Context context, FnID id, String argName,
      Type formalArgT, Type argExprT,
      MultiMap<String, Type> tvConstraints, boolean throwOnFail)
          throws TypeMismatchException {
    if (Types.isUnion(formalArgT)) {
      throw new TypeMismatchException(context, "Union type " + formalArgT +
          " with type variable is not supported");
    }

    if (Types.isRef(argExprT)) {
      // Will be dereferenced
      argExprT = argExprT.memberType();
    }
    if (Types.isUnion(argExprT)) {
      List<Map<String, Type>> possible = new ArrayList<Map<String,Type>>();
      for (Type alt: UnionType.getAlternatives(argExprT)) {
        possible.add(formalArgT.matchTypeVars(alt));
      }
      // Sanity check: ensure that all bind the same type variables
      for (Map<String, Type> m: possible) {
        assert(m.keySet().equals(possible.get(0).keySet()));
      }

      for (String boundVar: possible.get(0).keySet()) {
        List<Type> choices = new ArrayList<Type>();
        for (Map<String, Type> m: possible) {
          Type t = m.get(boundVar);
          assert(!Types.isUnion(t)); // Shouldn't be union inside union
          choices.add(t);
        }
        tvConstraints.put(boundVar, UnionType.makeUnion(choices));
      }
    } else {
      Map<String, Type> matchedTypeVars = formalArgT.matchTypeVars(argExprT);
      if (throwOnFail && matchedTypeVars == null) {
        throw new TypeMismatchException(context, "Could not match type " +
            "variables for formal arg type " + formalArgT + " and argument " +
            " expression type: " + argExprT);
      }
      tvConstraints.putAll(matchedTypeVars);
    }
    // Leave type var
    return formalArgT;
  }

  /**
   * Find all possible function types from different type variable bindings.
   * @param context
   * @param id
   * @param fnType
   * @param specificInputs
   * @param bindings
   * @return at least one possible function type
   */
  private static List<FunctionType> findPossibleFunctionTypes(Context context,
      FnID id, FunctionType fnType, List<Type> specificInputs,
      Map<String, List<Type>> bindings) {
    List<String> typeVars = new ArrayList<String>(fnType.getTypeVars());

    // Handle case where type variables are unbound
    for (ListIterator<String> it = typeVars.listIterator(); it.hasNext(); ) {
      if (!bindings.containsKey(it.next())) {
        it.remove();
      }
    }

    int currChoices[] = new int[typeVars.size()]; // initialized to zero

    List<FunctionType> possibilities = new ArrayList<FunctionType>();
    while (true) {
      Map<String, Type> currBinding = new HashMap<String, Type>();
      for (int i = 0; i < currChoices.length; i++) {
        String tv = typeVars.get(i);
        currBinding.put(tv, bindings.get(tv).get(currChoices[i]));
      }

      possibilities.add(constructFunctionType(fnType, specificInputs, currBinding));

      int pos = currChoices.length - 1;
      while (pos >= 0 &&
          currChoices[pos] >= bindings.get(typeVars.get(pos)).size() - 1) {
        pos--;
      }
      if (pos < 0) {
        break;
      } else {
        currChoices[pos]++;
        for (int i = pos + 1; i < currChoices.length; i++) {
          currChoices[i] = 0;
        }
      }
    }

    assert(possibilities.size() >= 1);
    return possibilities;
  }

  private static FunctionType constructFunctionType(FunctionType abstractType,
      List<Type> inputs, Map<String, Type> binding) {
    List<Type> concreteInputs = bindTypeVariables(inputs, binding);
    List<Type> concreteOutputs = bindTypeVariables(
        abstractType.getOutputs(), binding);
    return new FunctionType(concreteInputs, concreteOutputs, false);
  }

  /**
   * Match abstract argument types to provided argument expressions,
   * Expand variable-length arguments if needed.  Leaves nulls if optional
   * argument was omitted.
   * @param context
   * @param overload
   * @param argTypes
   * @param kwArgTypes
   * @param throwOnFail on failure, return null if false
   *                      or throw exception if true
   * @return
   * @throws TypeMismatchException
   * @throws InvalidConstructException
   */
  private static List<MatchedArg> matchArgs(Context context,
      FnOverload overload, List<Type> argTypes, Map<String, Type> kwArgTypes,
      boolean throwOnFail) throws TypeMismatchException {
    FunctionType fnType = overload.type;
    DefaultVals<Var> defaultVals = overload.defaultVals;

    // Don't have to deal with both varArgs and defaults
    assert(!fnType.hasVarargs() ||
           !defaultVals.hasAnyDefaults());

    List<Type> abstractInputs = fnType.getInputs();
    int numPosArgs = argTypes.size();
    int numKwArgs = kwArgTypes.size();
    int numTotalArgs = numPosArgs + numKwArgs;

    boolean fixedLength = !defaultVals.hasAnyDefaults() &&
                          !fnType.hasVarargs();
    int minNumArgs;
    if (defaultVals.hasAnyDefaults()) {
      minNumArgs = defaultVals.firstDefault();
    } else if (fnType.hasVarargs()) {
      minNumArgs = fnType.getInputs().size() - 1;
    } else {
      minNumArgs = fnType.getInputs().size();
    }

    if (numTotalArgs < minNumArgs) {
      if (throwOnFail) {
        throw new TypeMismatchException(context,  "Too few arguments in "
          + "call to function " + overload.id.originalName() + ": expected "
          + (fixedLength ? "==" : ">=") + " " + minNumArgs
          + " but got " + numTotalArgs);
      }
      return null;
    }

    if (!fnType.hasVarargs() && numPosArgs > abstractInputs.size()) {
      if (throwOnFail) {
        throw new TypeMismatchException(context,  "Wrong number of arguments in "
            + "call to function " + overload.id.originalName()
            + ": expected at most " + abstractInputs.size()
            + " but got " + numPosArgs + " positional arguments");
      }
      return null;
    }

    int numMatchedArgs = fnType.hasVarargs() ? numTotalArgs
                                : fnType.getInputs().size();

    Set<String> unmatchedKwArgs = new HashSet<String>(kwArgTypes.keySet());
    MatchedArg matched[] = new MatchedArg[numMatchedArgs];
    for (int i = 0; i < numMatchedArgs; i++) {
      String name;
      Type formalArgType;
      Type argExprType;
      if (i < numPosArgs) {
        argExprType = argTypes.get(i);
        int formalArgIx;
        if (i < abstractInputs.size()) {
          formalArgIx = i;
        } else {
          assert(fnType.hasVarargs());
          formalArgIx = abstractInputs.size() - 1;
        }
        name = overload.inArgNames.get(formalArgIx);
        formalArgType = abstractInputs.get(formalArgIx);
      } else {
        assert(!fnType.hasVarargs());
        name = overload.inArgNames.get(i);
        boolean hasDefault = overload.defaultVals.defaultVals().get(i) != null;

        if (!hasDefault) {
          throw new TypeMismatchException(context,
                  "Keyword arguments cannot be used for required argument "
                 + name + " in call to " + overload.id.originalName());
        }
        formalArgType = abstractInputs.get(i);
        argExprType = kwArgTypes.get(name);
        if (argExprType != null) {
          unmatchedKwArgs.remove(name);
        }
      }

      matched[i] = new MatchedArg(name, formalArgType, argExprType);
    }

    if (unmatchedKwArgs.size() > 0) {
      throw new TypeMismatchException(context, "Keyword arguments "
          + "not matched in call to " + overload.id.originalName()
          + ": " + unmatchedKwArgs);
    }

    return Arrays.asList(matched);
  }

  /**
   * Expand varArgs to at most maxLen
   * @param inputs
   * @param maxLen
   * @return
   */
  private static List<Type> expandVarArgs(List<Type> inputs, int maxLen) {
    List<Type> expandedInputs = new ArrayList<Type>();
    expandedInputs.addAll(inputs.subList(0, inputs.size() - 1));

    Type varArgType = inputs.get(inputs.size() - 1);
    for (int i = inputs.size() - 1; i < maxLen; i++) {
      expandedInputs.add(varArgType);
    }
    return expandedInputs;
  }

  private static List<Type> bindTypeVariables(List<Type> types,
      Map<String, Type> binding) {
    List<Type> res = new ArrayList<Type>(types.size());
    for (Type type: types) {
      res.add(type.bindTypeVars(binding));
    }
    return res;
  }

  /**
   * @param candidates MultiMap, with possible bindings for each type variable
   * @param throwOnFail if true, raise exception on matching failure,
   *                    otherwise return null
   * @return map of type variable name to possible types.  If list is null,
   *         this means matching failed
   * @throws TypeMismatchException if no viable binding
   */
  private static Map<String, List<Type>> unifyTypeVarConstraints(
      Context context, FnID id, List<String> typeVars,
      MultiMap<String, Type> candidates, boolean throwOnFail)
          throws TypeMismatchException {
    Map<String, List<Type>> possible = new HashMap<String, List<Type>>();
    /* Check whether type variables were left unbound */
    for (String typeVar: typeVars) {
      List<Type> cands = candidates.get(typeVar);
      if (cands == null || cands.size() == 0) {
        LogHelper.debug(context, "Type variable " + typeVar + " for call to " +
            "function " + id.originalName() + " was unbound");
      } else {
        List<Type> intersection = Types.typeIntersection(cands);
        if (intersection.size() == 0) {
          if (throwOnFail) {
            throw new TypeMismatchException(context,
              "Type variable " + typeVar + " for call to function " +
                  id.originalName() + " could not be bound to concrete type: no " +
                  "intersection between types: " + cands);
          }
          return null;
        }

        possible.put(typeVar, intersection);
      }
    }
    return possible;
  }

  private static TypeMismatchException argumentTypeException(Context context,
      String argName, Type expType, Type actType, String errContext) {
    return new TypeMismatchException(context, "Expected argument " +
        argName + " to have one of the following types: "
        + expType.typeName() + ", but had type: " + actType.typeName()
        + errContext);
  }

  private static TypeMismatchException overloadMatchFailException(
      Context context, FnCallInfo fc, String cause) {
    StringBuilder sb = new StringBuilder();
    sb.append("Function input argument types: ");
    sb.append(typeList(fc.argTypes));
    sb.append(" " + cause + " " + fc.name + ".\n");
    sb.append("Overload input types were: \n");
    for (FnOverload fnType: fc.fnTypes) {
      sb.append(typeList(fnType.type.getInputs()));
      sb.append('\n');
    }

    return new TypeMismatchException(context, sb.toString());
  }

  public static void checkOverloadAllowed(Context context, FnID overloadID,
       FunctionType type, boolean hasOptionalArg) throws InvalidOverloadException {
    for (Type inType: type.getInputs()) {
      if (!inType.isConcrete()) {
        throw new InvalidOverloadException(context,
            "Invalid input argument type " + inType.typeName() + " for " +
            "overloaded function " + overloadID.originalName() + ". " +
            "Overloaded functions cannot have polymorphic input arguments");
      }
    }

    if (hasOptionalArg) {
      throw new InvalidOverloadException(context, "Cannot overload function "
                       + overloadID.originalName() + " with default value");
    }
  }

  protected static boolean hasOptionalArg(List<Arg> defaultVals) {
    boolean hasOptionalArg = false;
    for (Arg defaultVal: defaultVals) {
      if (defaultVal != null) {
        hasOptionalArg = true;
        break;
      }
    }
    return hasOptionalArg;
  }

  /**
   * Check to see if overloaded functions are potentially ambiguous
   * @param fakeContext
   * @param ft
   * @param ft2
   * @throws InvalidOverloadException
   */
  public static void checkOverloadsAmbiguity(Context context, String functionName,
      FunctionType ft1, FunctionType ft2) throws InvalidOverloadException {

    if (ft1.getOutputs().size() != ft2.getOutputs().size()) {
      throw new InvalidOverloadException("Overloads must have same number"
          + "of output arguments: " + ft1.getOutputs().size() + " vs " +
          ft2.getOutputs().size() + " for " + functionName);
    }

    // Need to handle non-varargs and varargs functions
    List<Type> in1 = ft1.getInputs(), in2 = ft2.getInputs();

    if (ft1.hasVarargs() && ft2.hasVarargs()) {
      // Extend shorter varargs up to last required arg of longer varargs
      int maxRequiredLen = Math.min(in1.size() - 1, in2.size() - 1);
      in1 = expandVarArgs(in1, maxRequiredLen);
      in1 = expandVarArgs(in2, maxRequiredLen);

    } else if (ft1.hasVarargs() || ft2.hasVarargs()) {
      // Attempt to expand varargs to match length of non-varargs
      if (ft1.hasVarargs()) {
        in1 = expandVarArgs(in1, in2.size());
      } else {
        in2 = expandVarArgs(in2, in1.size());
      }
    }

    if (in1.size() != in2.size()) {
      return;
    }

    // Check pairwise to see if a type alternative of one is assignable to the other
    for (int i = 0; i < in1.size(); i++) {
      if (unambiguousArg(in1.get(i), in2.get(i))) {
        // OK!
        return;
      }
    }

    // No unambiguous args
    throw new InvalidOverloadException("Overloads of function " + functionName
        + " are potentially ambiguous.  Function input types are: " + typeList(in1) +
        " and " + typeList(in2));
  }

  private static boolean unambiguousArg(Type inType1, Type inType2) {
    return !inType1.assignableTo(inType2) && !inType2.assignableTo(inType1);
  }

  private static String typeList(List<Type> types) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");

    boolean first = true;
    for (Type t: types) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(t.typeName());
    }
    sb.append(")");

    return sb.toString();
  }

  /**
   * Construct expression type from list of function matches.
   *
   * Note: this assumes that all alternatives have same number of outputs
   * @param f
   * @param matches
   * @return
   */
  private static Type exprTypeFromMatch(FnMatch match) {

    if (match.concreteAlts.size() == 1) {
      // Function type determined entirely by input type
      return TupleType.makeTuple(match.concreteAlts.get(0).getOutputs());
    } else {
      // Ambiguous type variable binding (based on inputs)
      assert(match.concreteAlts.size() >= 2);
      int numOutputs = numOutputs(match.concreteAlts);

      if (numOutputs == 0) {
        return TupleType.makeTuple();
      } else {
        // Turn into a list of UnionTypes
        List<Type> altOutputs = new ArrayList<Type>();
        for (int out = 0; out < numOutputs; out++) {
          List<Type> altOutput = new ArrayList<Type>();
          for (FunctionType ft: match.concreteAlts) {
            altOutput.add(ft.getOutputs().get(out));
          }
          altOutputs.add(UnionType.makeUnion(altOutput));
        }
        return TupleType.makeTuple(altOutputs);
      }
    }
  }

  private static int numOutputs(List<FunctionType> alts) {
    int numOutputs = alts.get(0).getOutputs().size();

    for (FunctionType alt: alts) {
      if (numOutputs != alt.getOutputs().size()) {
        throw new STCRuntimeError("Expected same number of outputs: " + alts);
      }
    }

    return numOutputs;
  }

  /**
   * Represent a matched function
   * @author tim
   */
  public static class FnMatch {
    /** Overload selected */
    FnOverload overload;

    /** Possible concrete types */
    public final List<FunctionType> concreteAlts;


    public FnMatch(FnOverload overload, List<FunctionType> concreteCandidates) {
      this.overload = overload;
      this.concreteAlts = concreteCandidates;
    }

    @Override
    public String toString() {
      return "FnMatch: " + overload + " " + concreteAlts;
    }
  }

  /**
   * Represent a matched function
   * @author tim
   */
  public static class ConcreteMatch {
    /** Overload of function selected */
    public final FnOverload overload;

    /** Concrete type of function selected */
    public final FunctionType type;

    public ConcreteMatch(FnOverload overload, FunctionType type) {
      this.overload = overload;
      this.type = type;
    }

    @Override
    public String toString() {
      return "ConcreteMatch: " + overload.id + " " + type;
    }
  }

  public static class FnCallInfo {
    public final String name;
    public final List<FnOverload> fnTypes;
    public final List<Type> argTypes;
    public final Map<String, Type> kwArgTypes;

    public FnCallInfo(String name, List<FnOverload> fnTypes,
          List<Type> argTypes,  Map<String, Type> kwArgTypes) {
      this.name = name;
      this.fnTypes = fnTypes;
      this.argTypes = argTypes;
      this.kwArgTypes = kwArgTypes;
    }
  }

  public static class MatchedArg {
    public final String name;
    public final Type formalArgType;
    /** Arg expr type, can be null for optional args */
    public final Type argExprType;

    public MatchedArg(String name, Type formalArgType, Type argExprType) {
      this.name = name;
      this.formalArgType = formalArgType;
      this.argExprType = argExprType;
    }

    @Override
    public String toString() {
      return name + ": " + formalArgType + " " + argExprType;
    }
  }
}
