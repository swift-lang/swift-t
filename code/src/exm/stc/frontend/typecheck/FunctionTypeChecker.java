package exm.stc.frontend.typecheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UserException;
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
    List<FunctionType> alts = concretiseFunctionCall(context, f);
    if (alts.size() == 1) {
      // Function type determined entirely by input type
      return TupleType.makeTuple(alts.get(0).getOutputs());
    } else {
      // Ambiguous type variable binding (based on inputs)
      assert(alts.size() >= 2);
      int numOutputs = f.fnType().getOutputs().size();
      if (numOutputs == 0) {
        return TupleType.makeTuple();
      } else {
        // Turn into a list of UnionTypes
        List<Type> altOutputs = new ArrayList<Type>();
        for (int out = 0; out < numOutputs; out++) {
          List<Type> altOutput = new ArrayList<Type>();
          for (FunctionType ft: alts) {
            altOutput.add(ft.getOutputs().get(out));
          }
          altOutputs.add(UnionType.makeUnion(altOutput));
        }
        return TupleType.makeTuple(altOutputs);
      }
    }
  }

  public static FunctionType concretiseFunctionCall(Context context,
          FunctionCall fc, List<Var> outputs) throws UserException {
    List<Type> outTs = new ArrayList<Type>(outputs.size());
    for (Var output: outputs) {
      checkFunctionOutputValid(context, fc, output);
      outTs.add(output.type());
    }
    List<FunctionType> alts = concretiseFunctionCall(context, fc);
    assert(alts.size() > 0);
    for (FunctionType alt: alts) {
      assert(alt.getOutputs().size() == outputs.size());

      MultiMap<String, Type> tvConstraints = new MultiMap<String, Type>();

      boolean match = true;
      for (int i = 0; i < outTs.size(); i++) {
        Type outT = outTs.get(i);
        Type outArgT = alt.getOutputs().get(i);

        if (outArgT.isConcrete()) {
          if (!outArgT.assignableTo(outT)) {
            match = false;
            break;
          }
        } else {
          Map<String, Type> bindings = outArgT.matchTypeVars(outT);
          if (bindings == null) {
            match = false;
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
            fc.function(), fc.fnType().getTypeVars(), tvConstraints);

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

      LogHelper.trace(context, "Call " + fc.function() + " alternative "
          + "function type " + alt + " match: " + match);

      // Choose first viable alternative
      if (match) {
        return alt;
      }
    }
    throw new TypeMismatchException(context, "Could not find consistent " +
        "binding for type variables.  Viable function signatures based on " +
        "input arguments were: " + alts + " but output types were " + outTs);
  }

  /**
   * Concrete input type for function
   * @throws TypeMismatchException
   */
  public static Pair<Type, Type>
  concretiseFnArg(Context context, String function, int argNum,
      Type formalArgT, Type argExprT) throws TypeMismatchException {
    Pair<Type, Type> res = selectArgType(formalArgT, argExprT, true);
    if (res == null) {
      throw argumentTypeException(context, argNum, formalArgT, argExprT,
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
   * Check that function output var is valid
   * @param output
   * @throws TypeMismatchException
   */
  private static void checkFunctionOutputValid(Context context,
      FunctionCall f, Var output) throws TypeMismatchException {
    if (Types.isFile(output) && output.isMapped() == Ternary.FALSE &&
        !output.type().fileKind().supportsTmpImmediate() &&
        !context.getForeignFunctions().canInitOutputMapping(f.function())) {
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
   * @param context
   * @param abstractType function type with varargs, typevars, union types
   * @param args input argument expressions
   * @param noWarn don't issue warnings
   * @return list of possible concrete function types with varargs, typevars
   *          and union type args removed
   * @throws UserException
   */
  private static List<FunctionType> concretiseFunctionCall(Context context,
      FunctionCall fc) throws UserException {
    List<Type> argTypes = new ArrayList<Type>(fc.args().size());
    for (SwiftAST arg: fc.args()) {
      argTypes.add(TypeChecker.findExprType(context, arg));
    }

    // Expand varargs
    List<Type> expandedInputs = expandVarargs(context, fc);

    // Narrow down possible bindings - choose union types
    // find possible typevar bindings
    List<Type> specificInputs = new ArrayList<Type>(fc.args().size());
    MultiMap<String, Type> tvConstraints = new MultiMap<String, Type>();
    for (int i = 0; i < fc.args().size(); i++) {
      Type exp = expandedInputs.get(i);
      Type act = argTypes.get(i);
      // a more specific type than expected
      Type exp2;
      exp2 = narrowArgType(context, fc, i, exp, act, tvConstraints);
      specificInputs.add(exp2);
    }
    LogHelper.trace(context, "Call " + fc.function() + " specificInputs: " +
        specificInputs + " possible bindings: " + tvConstraints);

    // Narrow down type variable bindings depending on constraints
    Map<String, List<Type>> bindings = unifyTypeVarConstraints(context,
        fc.function(), fc.fnType().getTypeVars(), tvConstraints);

    LogHelper.trace(context, "Call " + fc.function() + " unified bindings: " +
        tvConstraints);

    List<FunctionType> possibilities = findPossibleFunctionTypes(context,
                                           fc, specificInputs, bindings);

    LogHelper.trace(context, "Call " + fc.function() + " possible concrete types: " +
        possibilities);

    if (possibilities.size() == 0) {
      throw new TypeMismatchException(context, "Arguments for call to " +
          "function " + fc.function() + " were incompatible with function " +
          "type.  Function input types were: " + fc.fnType().getInputs() +
          ", argument types were " + argTypes);
    }

    return possibilities;
  }

  /**
   * Narrow down the possible argument types for a function call
   * @param context
   * @param fc
   * @param arg number of argument
   * @param formalArgT Formal argument type from abstract function type
   * @param argExprT Type of argument expression for function
   * @param tvConstrains Filled in with constraints for each type variable
   * @return
   * @throws TypeMismatchException
   */
  private static Type narrowArgType(Context context, FunctionCall fc, int arg,
      Type formalArgT, Type argExprT,
      MultiMap<String, Type> tvConstrains)
          throws TypeMismatchException {
    Type narrowedFormalArgT;
    if (formalArgT.hasTypeVar()) {
      narrowedFormalArgT = checkFunArgTV(context, fc, arg, formalArgT,
          argExprT, tvConstrains);
    } else {
      narrowedFormalArgT = checkFunArg(context, fc, arg, formalArgT, argExprT);
    }

    return narrowedFormalArgT;
  }


  /**
   * Check function input argument type
   * Returns a tuple indicating which formal argument type is selected and
   * what type the input argument expression should be interpreted as having.
   * Does not handle type variables
   * @param context
   * @param function
   * @param argNum
   * @param formalArgT
   * @param argExprT
   * @return selected formal argument type
   * @throws TypeMismatchException
   */
  private static Type checkFunArg(Context context,
      FunctionCall fc, int argNum, Type formalArgT,
      Type argExprT) throws TypeMismatchException {
    assert(!formalArgT.hasTypeVar()) : formalArgT;

    Pair<Type, Type> alt = selectArgType(formalArgT, argExprT, false);

    if (alt == null) {
      throw argumentTypeException(context, argNum, formalArgT, argExprT,
          " in call to function " + fc.function());
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
   * @param func
   * @param arg
   * @param formalArgT
   * @param argExprT
   * @param tvConstraints fill in constraints for type variables
   * @return
   * @throws TypeMismatchException
   */
  private static Type checkFunArgTV(Context context, FunctionCall fc, int arg,
      Type formalArgT, Type argExprT,
      MultiMap<String, Type> tvConstraints)
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
      if (matchedTypeVars == null) {
        throw new TypeMismatchException(context, "Could not match type " +
            "variables for formal arg type " + formalArgT + " and argument " +
            " expression type: " + argExprT);
      }
      tvConstraints.putAll(matchedTypeVars);
    }
    // Leave type var
    return formalArgT;
  }

  private static List<FunctionType> findPossibleFunctionTypes(Context context,
      FunctionCall fc, List<Type> specificInputs, Map<String, List<Type>> bindings)
          throws TypeMismatchException {
    List<String> typeVars = new ArrayList<String>(fc.fnType().getTypeVars());

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

      possibilities.add(constructFunctionType(fc.fnType(), specificInputs,
          currBinding));

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
    return possibilities;
  }

  private static FunctionType constructFunctionType(FunctionType abstractType,
      List<Type> inputs, Map<String, Type> binding) {
    List<Type> concreteInputs = bindTypeVariables(inputs, binding);
    List<Type> concreteOutputs = bindTypeVariables(
        abstractType.getOutputs(), binding);
    return new FunctionType(concreteInputs, concreteOutputs, false);
  }

  private static List<Type> expandVarargs(Context context,
      FunctionCall fc)
          throws TypeMismatchException {
    FunctionType abstractType = fc.fnType();
    int numArgs = fc.args().size();

    List<Type> abstractInputs = abstractType.getInputs();
    if (abstractType.hasVarargs()) {
      if (numArgs < abstractInputs.size() - 1) {
        throw new TypeMismatchException(context,  "Too few arguments in "
            + "call to function " + fc.function() + ": expected >= "
            + (abstractInputs.size() - 1) + " but got " + numArgs);
      }
    } else if (abstractInputs.size() != numArgs) {
      throw new TypeMismatchException(context,  "Wrong number of arguments in "
          + "call to function " + fc.function() + ": expected "
          + abstractInputs.size() + " but got " + numArgs);
    }

    if (abstractType.hasVarargs()) {
      List<Type> expandedInputs;
      expandedInputs = new ArrayList<Type>();
      expandedInputs.addAll(abstractInputs.subList(0,
          abstractInputs.size() - 1));
      Type varArgType = abstractInputs.get(abstractInputs.size() - 1);
      for (int i = abstractInputs.size() - 1; i < numArgs; i++) {
        expandedInputs.add(varArgType);
      }
      return expandedInputs;
    } else {
      return abstractInputs;
    }
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
   * @return map of type variable name to possible types.  If list is null,
   *    this means no constraint on type variable.
   * @throws TypeMismatchException if no viable binding
   */
  private static Map<String, List<Type>> unifyTypeVarConstraints(
      Context context, String function, List<String> typeVars,
      MultiMap<String, Type> candidates)
          throws TypeMismatchException {
    Map<String, List<Type>> possible = new HashMap<String, List<Type>>();
    /* Check whether type variables were left unbound */
    for (String typeVar: typeVars) {
      List<Type> cands = candidates.get(typeVar);
      if (cands == null || cands.size() == 0) {
        LogHelper.debug(context, "Type variable " + typeVar + " for call to " +
            "function " + function + " was unbound");
      } else {
        List<Type> intersection = Types.typeIntersection(cands);
        if (intersection.size() == 0) {
          throw new TypeMismatchException(context,
              "Type variable " + typeVar + " for call to function " +
                  function + " could not be bound to concrete type: no " +
                  "intersection between types: " + cands);
        }


        possible.put(typeVar, intersection);
      }
    }
    return possible;
  }

  private static TypeMismatchException argumentTypeException(Context context,
      int argPos, Type expType, Type actType, String errContext) {
    return new TypeMismatchException(context, "Expected argument " +
        (argPos + 1) + " to have one of the following types: "
        + expType.typeName() + ", but had type: " + actType.typeName()
        + errContext);
  }
}
