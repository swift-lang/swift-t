package exm.stc.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.frontend.tree.FunctionDecl;
import exm.stc.frontend.tree.InlineCode;
import exm.stc.frontend.tree.Literals;
import exm.stc.ic.STCMiddleEnd;
import exm.stc.tclbackend.TclOpTemplate;

/**
 * Manage wrapper functions
 *
 */
public class WrapperGen {

  private final STCMiddleEnd backend;

  /**
   * Saved wrappers
   */
  private final Map<FnID, SavedWrapper> saved
                  = new HashMap<FnID, SavedWrapper>();

  /**
   * Wrappers that have already been generated
   */
  private final ListMultimap<FnID, GeneratedWrapper> generated
                  = ArrayListMultimap.create();

  /**
   * Used function names to avoid duplicates
   */
  private final Set<String> usedFunNames = new HashSet<String>();

  public WrapperGen(STCMiddleEnd backend) {
    this.backend = backend;
  }

  public TclOpTemplate loadTclTemplate(Context context, FnID id,
          FunctionDecl fdecl, FunctionType ft, SwiftAST inlineTclTree)
          throws InvalidSyntaxException, UserException {
    assert(inlineTclTree.getType() == ExMParser.INLINE_TCL);

    checkInlineTclTypes(context, id, ft, false);
    TclOpTemplate inlineTcl;
    assert(inlineTclTree.getChildCount() == 1);
    String tclTemplateString =
          Literals.extractLiteralString(context, inlineTclTree.child(0));
    inlineTcl = InlineCode.templateFromString(context, tclTemplateString);

    List<String> inNames = fdecl.getInNames();
    inlineTcl.addInNames(inNames);
    if (ft.hasVarargs()) {
      inlineTcl.setVarArgIn(inNames.get(inNames.size() - 1));
    }
    inlineTcl.addOutNames(fdecl.getOutNames());
    inlineTcl.verifyNames(context);
    context.getForeignFunctions().addLocalImpl(id, id);
    return inlineTcl;
  }

  /**
   * Check that the compiler can handle TCL templates with these
   * argument types
   * @param function
   * @param ftype
   * @param concreteType true if this is type with all polymorphism
   *                      removed
   */
  private void checkInlineTclTypes(Context context,
      FnID id, FunctionType ftype, boolean concreteType)
    throws TypeMismatchException {
    for (Type in: ftype.getInputs()) {

      List<Type> alts = UnionType.getAlternatives(in);
      assert(!concreteType || alts.size() == 1) :
          "polymorphic type but concrete expected " + in;
      for (Type alt: alts) {
        if (Types.isPrimFuture(alt)) {
          // OK
        } else if (Types.isPrimUpdateable(alt)) {
          // OK
        } else if (Types.isContainer(alt) || Types.isStruct(alt)) {
          // OK: can store
          // TODO: check for recursive?
        } else if (!concreteType &&
                (Types.isWildcard(alt) || Types.isTypeVar(alt))) {
          // Defer checking until type parameters filled in
        } else {
          throw new TypeMismatchException(context,
              "Type " + alt.typeName() + " is"
              + " not currently supported as an input to inline TCL code"
              + " for function " + id.originalName());
        }
      }
    }
    for (Type out: ftype.getOutputs()) {
      List<Type> alts = UnionType.getAlternatives(out);
      assert(!concreteType || alts.size() == 1) :
          "polymorphic type but concrete expected " + out;
      for (Type alt: alts) {
        if (Types.isContainer(alt) || Types.isStruct(alt)) {
          // OK: can store
          // TODO: check for recursive?
        } else if (Types.isPrimUpdateable(alt)) {
          // OK: will pass in standard repr
        } else if (Types.isPrimFuture(alt)) {
          // OK
        } else if (!concreteType &&
            (Types.isWildcard(alt) || Types.isTypeVar(alt))) {
          // Defer checking until type parameters filled in
        } else {
          throw new TypeMismatchException(context,
              "Type " + alt.typeName() + " is"
              + " not currently supported as an out to inline TCL code"
              + " for function " + id.originalName());
        }
      }
    }
  }

  /**
   * Save a wrapper for future use.
   * @param function
   * @param ft
   * @param decl
   * @param taskMode
   * @param isParallel
   * @param isTargetable
   */
  public void saveWrapper(Context context, FnID id, FunctionType ft,
      FunctionDecl decl, ExecTarget taskMode, boolean isParallel,
      boolean isTargetable) {
    assert(context.getForeignFunctions().hasLocalImpl(id)) :
              "Expected inline version for " + id;
    SavedWrapper wrapper = new SavedWrapper(id, ft, decl, taskMode,
                                    isParallel, isTargetable);
    saved.put(id, wrapper);
  }

  /**
   * Actually generate the wrapper for an invocation of a function with
   * specific argument types, returning a previously created one if this
   * has already been done.
   * @param function
   * @param concrete the actual type of input and output vars
   * @throws UserException
   * @returns the name of the function that should be called
   */
  public FnID generateWrapper(Context context, FnID id,
                                FunctionType concrete) throws UserException {
    SavedWrapper wrapper = saved.get(id);
    assert(wrapper != null) : "Unsaved wrapper " + id;

    for (GeneratedWrapper gen: generated.get(id)) {
      if (concrete.equals(gen.concrete)) {
        // We already generated one with the right type
        return gen.generatedID;
      }
    }

    return generateWrapper(context, wrapper, concrete);
  }

  /**
   * Generate a function that wraps some inline tcl
   * @returns generated function name
   */
  private FnID generateWrapper(Context context, SavedWrapper wrapper,
        FunctionType concrete) throws UserException {
    if (wrapper.isParallel) {
      //TODO: figure out what output types are valid
      throw new STCRuntimeError("Don't support wrapping parallel functions yet");
    }

    // Use sorted map to order type vars by name
    SortedMap<String, Type> typeVarBindings = new TreeMap<String, Type>();
    // Track which variables are chosen for unions
    List<Type> unionBindings = new ArrayList<Type>();

    // fill in type vars if needed
    int nIn = concrete.getInputs().size();
    int nOut = concrete.getOutputs().size();

    List<Var> inVars = wrapper.decl.getInVars(context);
    assert(nIn == inVars.size());
    List<Var> outVars = wrapper.decl.getOutVars(context);
    assert(nOut == outVars.size());

    List<Var> concreteIn = new ArrayList<Var>(nIn);
    List<Var> concreteOut = new ArrayList<Var>(nOut);

    for (int i = 0; i < nIn; i++) {
      Var in = inVars.get(i);
      Type concreteT = concrete.getInputs().get(i);
      concreteIn.add(in.substituteType(concreteT));
      updateTypeInfo(typeVarBindings, unionBindings, in.type(), concreteT);
    }

    for (int i = 0; i < nOut; i++) {
      Var out = outVars.get(i);
      Type concreteT = concrete.getOutputs().get(i);
      concreteOut.add(out.substituteType(concreteT));
      updateTypeInfo(typeVarBindings, unionBindings, out.type(), concreteT);
    }

    // generate function name based on type
    FnID wrapperID = chooseWrapperID(context, wrapper.original,
                              typeVarBindings, unionBindings);

    // Check concrete types that were substituted are ok
    checkInlineTclTypes(context, wrapper.original, concrete, true);

    backend.generateWrappedBuiltin(wrapperID, wrapper.original,
          VarRepr.backendFnType(concrete), VarRepr.backendVars(concreteOut),
          VarRepr.backendVars(concreteIn), wrapper.taskMode, wrapper.isParallel,
          wrapper.isTargetable);

    // Save for later use
    GeneratedWrapper genWrapper = new GeneratedWrapper(wrapperID, concrete);
    generated.put(wrapper.original, genWrapper);


    // Copy over template
    context.getForeignFunctions().addLocalImpl(wrapperID, wrapper.original);
    return wrapperID;
  }

  private void updateTypeInfo(SortedMap<String, Type> typeVarBindings,
      List<Type> unionChoices, Type abstractT, Type concreteT) {

    // Can't match type vars for union
    if (Types.isUnion(abstractT)) {
      boolean found = false;
      for (Type opt: UnionType.getAlternatives(abstractT)) {
        if (opt.equals(concreteT)) {
          found = true;
        }
      }
      assert (found) : "No match for concrete type " + concreteT + " in " +
                       "union " + abstractT;
      unionChoices.add(concreteT);
    } else {
      Map<String, Type> b = abstractT.matchTypeVars(concreteT);

      assert(b != null) : abstractT + " " + concreteT;
      for (String tv: b.keySet()) {
        assert(!typeVarBindings.containsKey(tv) ||
                typeVarBindings.get(tv).equals(b.get(tv)));
      }
      typeVarBindings.putAll(b);
    }
  }

  private FnID chooseWrapperID(Context context, FnID originalID,
      SortedMap<String, Type> typeVarBindings, List<Type> unionBindings) {
    String prefix = originalID.uniqueName();

    // avoid clash with user functions by using invalid characters : and =
    for (Entry<String, Type> tv: typeVarBindings.entrySet()) {
      prefix += ":" + tv.getKey() + "=" + tv.getValue().typeName();
    }

    for (Type t: unionBindings) {
      prefix += ":" + t.typeName();
    }

    // Replace invalid characters, e.g. not valid in Tcl function name
    prefix = prefix.replaceAll("[\\[\\]]", "_");

    int attempt = 0;
    String trial = prefix;
    while (usedFunNames.contains(trial)) {
      trial = prefix + ":" + attempt;
      attempt++;
    }
    usedFunNames.add(trial);

    if (trial.equals(originalID.uniqueName())) {
      return originalID;
    }

    FnID wrapperID = new FnID(trial, originalID.originalName());

    // Copy over properties of original
    context.getForeignFunctions().copyProperties(wrapperID, originalID);

    return wrapperID;
  }

  /**
   * Information required for generation of a wrapper.
   */
  static class SavedWrapper {
    final FnID original;
    final FunctionType type;
    final FunctionDecl decl;
    final ExecTarget taskMode;
    final boolean isParallel;
    final boolean isTargetable;

    public SavedWrapper(FnID original, FunctionType type, FunctionDecl decl,
        ExecTarget taskMode, boolean isParallel, boolean isTargetable) {
      this.original = original;
      this.type = type;
      this.decl = decl;
      this.taskMode = taskMode;
      this.isParallel = isParallel;
      this.isTargetable = isTargetable;
    }
  }

  /**
   * Information about a wrapper that has already been generated
   */
  static class GeneratedWrapper {
    final FnID generatedID;
    final FunctionType concrete;

    public GeneratedWrapper(FnID generatedID,
                            FunctionType concrete) {
      this.generatedID = generatedID;
      this.concrete = concrete;
    }
  }
}
