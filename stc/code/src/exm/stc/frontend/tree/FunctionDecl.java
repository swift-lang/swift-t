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
package exm.stc.frontend.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.TypeVariable;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.frontend.Context;
import exm.stc.frontend.ExprWalker;
import exm.stc.frontend.LocalContext;
import exm.stc.frontend.VarCreator;

public class FunctionDecl {
  private final FunctionType ftype;
  private final ArrayList<String> inNames;
  private final ArrayList<String> outNames;
  private final DefaultVals<Var> defaultVals;

  private FunctionDecl(FunctionType ftype, ArrayList<String> inNames,
         ArrayList<String> outNames, DefaultVals<Var> defaultVals) {
    super();
    this.ftype = ftype;
    this.inNames = inNames;
    this.outNames = outNames;
    this.defaultVals = defaultVals;
  }

  public FunctionType getFunctionType() {
    return ftype;
  }

  public List<String> getInNames() {
    return Collections.unmodifiableList(inNames);
  }

  public DefaultVals<Var> defaultVals() {
    return defaultVals;
  }

  public List<String> getOutNames() {
    return Collections.unmodifiableList(outNames);
  }


  private static class ArgDecl {
    final String name;
    final Type type;
    /** Default value if any (null otherwise) */
    final Var defaultVal;
    final boolean varargs;

    private ArgDecl(String name, Type type, Var defaultVal, boolean varargs) {
      this.name = name;
      this.type = type;
      this.defaultVal = defaultVal;
      this.varargs = varargs;
    }
  }

  public static FunctionDecl fromAST(Context context, VarCreator varCreator,
      ExprWalker exprWalker, String function,
      SwiftAST inArgTree, SwiftAST outArgTree, Set<String> typeParams)
       throws UserException {
    LocalContext typeVarContext = LocalContext.fnContext(context, function);

    for (String typeParam: typeParams) {
      typeVarContext.defineType(typeParam, new TypeVariable(typeParam));
    }

    assert(inArgTree.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(outArgTree.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    ArrayList<String> inNames = new ArrayList<String>();
    ArrayList<Type> inArgTypes = new ArrayList<Type>();
    ArrayList<Var> defaultVector = new ArrayList<Var>();
    boolean hasDefault = false;

    boolean varArgs = false;
    for (int i = 0; i < inArgTree.getChildCount(); i++) {
      ArgDecl argInfo = extractArgInfo(typeVarContext, varCreator, exprWalker,
                                       inArgTree.child(i));
      inNames.add(argInfo.name);
      inArgTypes.add(argInfo.type);
      defaultVector.add(argInfo.defaultVal);
      hasDefault = hasDefault || argInfo.defaultVal != null;

      if (argInfo.defaultVal == null && hasDefault) {
        throw new TypeMismatchException(context, "argument " + argInfo.name
            + " in definition of function " + function + " comes after"
            + " a previous argument with a default value, but does not"
            + " specify a default");
      }

      if (argInfo.varargs) {
        if (i != inArgTree.getChildCount() - 1) {
          throw new TypeMismatchException(context, "variable argument marker "
              + "... must be in final position of input argument list");
        }
        if (hasDefault) {
          throw new TypeMismatchException(context, "Cannot provide default "
              + "values for argument in definition of function " + function
              + " with variable-length argument list");
        }
        varArgs = true;
      }
    }
    assert(inNames.size() == inArgTypes.size());

    ArrayList<String> outNames = new ArrayList<String>();
    ArrayList<Type> outArgTypes = new ArrayList<Type>();
    for (int i = 0; i < outArgTree.getChildCount(); i++) {
      ArgDecl argInfo = extractArgInfo(typeVarContext, varCreator, exprWalker,
                                       outArgTree.child(i));
      if (argInfo.varargs) {
        throw new TypeMismatchException(context, "cannot have variable" +
                " argument specifier ... in output list");
      } else if (Types.isUnion(argInfo.type)) {
        throw new TypeMismatchException(context, "Cannot have" +
                " union function output type: " + argInfo.type.typeName());
      } else {
        outArgTypes.add(argInfo.type);
        outNames.add(argInfo.name);
      }
    }
    assert(outNames.size() == outArgTypes.size());

    checkDuplicateArgs(context, function, inNames, outNames);

    FunctionType ftype;
    ftype = new FunctionType(inArgTypes, outArgTypes, varArgs, typeParams);
    DefaultVals<Var> defaultVals =
                           DefaultVals.fromDefaultValVector(defaultVector);
    return new FunctionDecl(ftype, inNames, outNames, defaultVals);
  }

  private static ArgDecl extractArgInfo(Context context, VarCreator varCreator,
      ExprWalker exprWalker, SwiftAST arg) throws UserException {
    assert(arg.getType() == ExMParser.DECLARATION);
    assert(arg.getChildCount() == 2 || arg.getChildCount() == 3);
    SwiftAST baseTypes = arg.child(0);
    SwiftAST restDecl = arg.child(1);
    assert(restDecl.getType() == ExMParser.DECLARE_VARIABLE_REST);

    // Handle alternative types
    List<Type> altPrefixes = TypeTree.extractMultiType(context, baseTypes);
    assert(altPrefixes.size() > 0);
    ArrayList<Type> alts = new ArrayList<Type>(altPrefixes.size());
    String argName = null;
    for (Type altPrefix: altPrefixes) {
      // Construct var in order to apply array markers and get full type
      Var v = fromFormalArgTree(context, altPrefix, restDecl, DefType.INARG);
      argName = v.name();
      alts.add(v.type());
    }
    Type argType = UnionType.makeUnion(alts);

    int nextArg = 2;
    boolean thisVarArgs = false;
    if (arg.getChildCount() > nextArg &&
        arg.child(nextArg).getType() == ExMParser.VARARGS) {
      nextArg++;
      thisVarArgs = true;
    }

    Var defaultVar = null;
    if (arg.getChildCount() > nextArg) {
      // TODO: the FunctionDecl is created twice, so two globals are created
      SwiftAST defaultValT = arg.child(nextArg++);

      Arg defaultVal = exprWalker.valueOfConstExpr(context, argType,
                                    defaultValT, argName);

      String constNamePrefix = Var.generateGlobalConstName(defaultVal);
      defaultVar = context.createGlobalConst(constNamePrefix, argType, true);
      varCreator.assignGlobalConst(context, defaultVar, defaultVal);
    }

    return new ArgDecl(argName, argType, defaultVar, thisVarArgs);
  }

  /**
    * Take a DECLARE_VARIABLE_REST subtree of the AST and return the appropriate declared
    * variable.  Doesn't check to see if variable already defined
    * @param context the current context, for info to add to error message
    * @param baseType the type preceding the declaration
    * @param tree a parse tree with the root a DECLARE_MULTI or DECLARE_SINGLE
    *                                                               subtree
    * @return
    * @throws UndefinedTypeException
   * @throws InvalidSyntaxException
    */
  public static Var fromFormalArgTree(
      Context context, Type baseType, SwiftAST tree, DefType deftype)
      throws UserException {
    assert(tree.getType() == ExMParser.DECLARE_VARIABLE_REST);
    assert(tree.getChildCount() >= 1);
    SwiftAST nameTree = tree.child(0);
    assert(nameTree.getType() == ExMParser.ID);
    String varName = nameTree.getText();

    // Process array markers to get final type
    List<SwiftAST> arrMarkers = tree.children(1);
    for (SwiftAST subtree: arrMarkers) {
      // Avoid internal errors if a mapping is applied in this context
      if (subtree.getType() == ExMParser.MAPPING) {
        throw new InvalidSyntaxException(context, "Cannot map function argument");
      }
    }

    Type varType = TypeTree.applyArrayMarkers(context, arrMarkers, baseType);

    return new Var(varType, varName, Alloc.STACK, deftype,
                   VarProvenance.userVar(context.getSourceLoc()));
  }

@SafeVarargs
private static void checkDuplicateArgs(Context context,
                                       String functionName,
                                       Collection<String> ...names)
throws DoubleDefineException {
    Set<String> usedNames = new HashSet<String>();

    for (Collection<String> names2: names) {
      for (String name: names2) {
        boolean added = usedNames.add(name);
        if (!added) {
          throw new DoubleDefineException(context, "Duplicate argument name " +
                                        name + " in function " + functionName);
        }
      }
    }
  }

  public List<Var> getInVars(Context context) {
    ArrayList<Var> inVars = new ArrayList<Var>(inNames.size());
    for (int i = 0; i < inNames.size(); i++) {
      Type t = ftype.getInputs().get(i);
      inVars.add(new Var(t, inNames.get(i), Alloc.STACK, DefType.INARG,
                VarProvenance.userVar(context.getSourceLoc())));
    }
    return inVars;
  }

  public List<Var> getOutVars(Context context) {
    ArrayList<Var> outVars = new ArrayList<Var>(outNames.size());
    for (int i = 0; i < outNames.size(); i++) {
      Type outType = ftype.getOutputs().get(i);
      outVars.add(new Var(outType, outNames.get(i), Alloc.STACK,
            DefType.OUTARG, VarProvenance.userVar(context.getSourceLoc())));
    }
    return outVars;
  }
}