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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.frontend.Context;
import exm.stc.frontend.Context.DefInfo;
import exm.stc.frontend.Context.DefKind;
import exm.stc.frontend.Context.FnOverload;
import exm.stc.frontend.LogHelper;
import exm.stc.frontend.typecheck.TypeChecker;

public class FunctionCall {
  public static enum FunctionCallKind {
    REGULAR_FUNCTION,
    STRUCT_CONSTRUCTOR,
    SUBTYPE_CONSTRUCTOR,
  }

  private final FunctionCallKind kind;
  private final String originalName;
  private final List<FnOverload> overloads;
  /* Positional arguments */
  private final List<SwiftAST> posArgs;
  /** Keyword arguments */
  private final Map<String, SwiftAST> kwArgs;

  private final Map<TaskPropKey, SwiftAST> annotationExprs;
  // Location (as location struct type) - null if unspecified
  private final SwiftAST location;
  // Override strictness of location (to support @soft_location annotation)
  private final boolean softLocationOverride;

  private FunctionCall(FunctionCallKind kind, String originalName,
      List<FnOverload> overloads, List<SwiftAST> posArgs,
      Map<String, SwiftAST> kwArgs,
      Map<TaskPropKey, SwiftAST> annotationExprs,
      SwiftAST location, boolean softLocationOverride) {
    this.kind = kind;
    this.originalName = originalName;
    this.overloads = overloads;
    this.posArgs = posArgs;
    this.kwArgs = kwArgs;
    this.annotationExprs = annotationExprs;
    this.location = location;
    this.softLocationOverride = softLocationOverride;
  }

  private static FunctionCall regularFunctionCall(String originalName,
      List<FnOverload> overloads, List<SwiftAST> posArgs,
      Map<String, SwiftAST> kwArgs,
      Map<TaskPropKey, SwiftAST> annotations,
      SwiftAST location, boolean softLocationOverride) {
    return new FunctionCall(FunctionCallKind.REGULAR_FUNCTION, originalName,
        overloads, posArgs, kwArgs,
        annotations, location, softLocationOverride);
  }

  private static FunctionCall structConstructor(String typeName,
      List<SwiftAST> posArgs, List<String> fieldNames, FunctionType ftype)
          throws InvalidSyntaxException {
    assert(ftype.getOutputs().size() == 1 &&
        Types.isStruct(ftype.getOutputs().get(0)));

    FnOverload fn = new FnOverload(constructorID(typeName), ftype,
                   fieldNames, DefaultVals.<Var>noDefaults(ftype));

    return new FunctionCall(FunctionCallKind.STRUCT_CONSTRUCTOR, typeName,
        fn.asList(), posArgs, Collections.<String, SwiftAST>emptyMap(),
        Collections.<TaskPropKey,SwiftAST>emptyMap(), null, false);
  }

  private static FunctionCall subtypeConstructor(String typeName,
      List<SwiftAST> posArgs, FunctionType ftype) {
        assert(ftype.getOutputs().size() == 1 &&
    Types.isSubType(ftype.getOutputs().get(0)));
    FnOverload fn = new FnOverload(constructorID(typeName), ftype,
                  Collections.<String>singletonList("value"),
                  DefaultVals.<Var>noDefaults(ftype));
    return new FunctionCall(FunctionCallKind.SUBTYPE_CONSTRUCTOR, typeName,
        fn.asList(), posArgs, Collections.<String, SwiftAST>emptyMap(),
        Collections.<TaskPropKey,SwiftAST>emptyMap(), null, false);
  }

  /**
   * Make function ID for type.  There should be no overloading of these.
   * @param typeName
   * @return
   */
  private static FnID constructorID(String typeName) {
    return new FnID(typeName, typeName);
  }

  public FunctionCallKind kind() {
    return kind;
  }

  public String originalName() {
    return originalName;
  }

  public List<FnOverload> overloads() {
    return overloads;
  }

  public List<SwiftAST> posArgs() {
    return posArgs;
  }

  public Map<String, SwiftAST> kwArgs() {
    return kwArgs;
  }

  public Map<TaskPropKey, SwiftAST> annotations() {
    return Collections.unmodifiableMap(annotationExprs);
  }

  /**
   * @return null if not location specified, the location expression otherwise
   */
  public SwiftAST location() {
    return location;
  }

  /**
   * @return whether the location should be interpreted as a soft location
   */
  public boolean softLocationOverride() {
    return softLocationOverride;
  }

  public static FunctionCall fromAST(Context context, SwiftAST tree,
          boolean doWarn) throws UserException {
    assert(tree.getChildCount() >= 2);
    SwiftAST fTree = tree.child(0);
    String f;
    if (fTree.getType() == ExMParser.DEPRECATED) {
      fTree = fTree.child(0);
      assert(fTree.getType() == ExMParser.ID);
      f = fTree.getText();
      if (doWarn) {
        LogHelper.warn(context, "Deprecated prefix @ in call to function " + f +
                " was ignored");
      }
    } else {
      assert(fTree.getType() == ExMParser.ID);
      f = fTree.getText();
    }

    SwiftAST arglist = tree.child(1);
    List<SwiftAST> posArgs = new ArrayList<SwiftAST>();
    Map<String, SwiftAST> kwArgs = new HashMap<String, SwiftAST>();
    for (SwiftAST argTree: arglist.children()) {
      if (argTree.getType() == ExMParser.KW_ARGUMENT) {
        SwiftAST argName = argTree.child(0);
        assert(argName.getType() == ExMParser.ID);
        SwiftAST argExpr = argTree.child(1);
        kwArgs.put(argName.getText(), argExpr);
      } else {
        if (kwArgs.size() > 0) {
          throw new InvalidSyntaxException(context, "Keyword arguments "
              + kwArgs.keySet() + " appeared before positional argument");
        }
        posArgs.add(argTree);
      }
    }

    DefInfo def = context.lookupDef(f);
    List<SwiftAST> annotations = tree.children(2);

    if (def == null) {
      throw UndefinedFunctionException.unknownFunction(context, f);
    } else if (def.kind == DefKind.FUNCTION) {
      return regularFunctionFromAST(context, annotations, f, posArgs, kwArgs,
                                    context.lookupFunction(f));
    } else if (def.kind == DefKind.TYPE) {
      Type type = context.lookupTypeUnsafe(f);
      assert(type != null);
      if (Types.isStruct(type)) {
        return structConstructorFromAST(context, annotations, f, posArgs,
                                        kwArgs, type);
      } else if (Types.isSubType(type)) {
        return subtypeConstructorFromAST(context, annotations, f, posArgs,
                                        kwArgs, type);
      }
    }
    throw new TypeMismatchException(f + " is not a function and "
                                    + "cannot be called");
  }

  private static FunctionCall regularFunctionFromAST(Context context,
      List<SwiftAST> annotationTs, String originalName, List<SwiftAST> posArgs,
      Map<String, SwiftAST> kwArgs, List<FnOverload> overloads)
      throws UserException, InvalidAnnotationException {
    Map<TaskPropKey, SwiftAST> annotations = new TreeMap<TaskPropKey, SwiftAST>();

    SwiftAST location = null;
    boolean softLocationOverride = false;
    for (SwiftAST annTree: annotationTs) {
      assert(annTree.getType() == ExMParser.CALL_ANNOTATION);
      assert(annTree.getChildCount() == 2);
      SwiftAST tag = annTree.child(0);
      SwiftAST expr = annTree.child(1);
      assert(tag.getType() == ExMParser.ID);
      String annotName = tag.getText();
      if (annotName.equals(Annotations.FNCALL_PAR)) {
        putAnnotationNoDupes(context, annotations, TaskPropKey.PARALLELISM,
                             expr);
      } else if (annotName.equals(Annotations.FNCALL_PRIO)) {
        putAnnotationNoDupes(context, annotations, TaskPropKey.PRIORITY, expr);
      } else if (annotName.equals(Annotations.FNCALL_LOCATION) ||
                 annotName.equals(Annotations.FNCALL_SOFT_LOCATION)) {
        if (location != null) {
          throw duplicateAnnotationException(context, "location");
        }
        location = expr;

        Type locType = TypeChecker.findExprType(context, location);
        if (!locType.assignableTo(Types.F_LOCATION)) {
          throw new TypeMismatchException(context, "Invalid location type."
              + " Expected type " + Types.F_LOCATION.typeName()
              + " actual type " + locType.typeName());
        }

        if (annotName.equals(Annotations.FNCALL_SOFT_LOCATION)) {
          softLocationOverride = true;
        }
      } else {
        throw new InvalidAnnotationException(context, "function call",
                                             annotName, false);
      }
    }

    return regularFunctionCall(originalName, overloads, posArgs, kwArgs,
                               annotations, location, softLocationOverride);
  }

  private static void putAnnotationNoDupes(Context context,
      Map<TaskPropKey, SwiftAST> annotations, TaskPropKey key, SwiftAST expr)
          throws UserException {
    SwiftAST prev = annotations.put(key, expr);
    if (prev != null) {
      throw duplicateAnnotationException(context, key.toString().toLowerCase());
    }
  }

  private static UserException duplicateAnnotationException(Context context,
                                                           String key) {
    return new UserException(context, "Duplicate function call annotation: " +
                                      key + " defined multiple times");
  }

  private static FunctionCall structConstructorFromAST(Context context,
      List<SwiftAST> annotations, String func, List<SwiftAST> posArgs,
      Map<String, SwiftAST> kwArgs, Type type)
          throws InvalidAnnotationException, InvalidSyntaxException {
    assert(Types.isStruct(type));

    if (kwArgs.size() > 0) {
      throw new InvalidSyntaxException(context,
          "Don't support keyword args for struct constructors");
    }

    if (annotations.size() > 0) {
      throw new InvalidAnnotationException(context, "Do not support "
          + "annotations for struct constructor (call to " + func + ")");
    }

    StructType structType = (StructType)type.getImplType();
    List<String> fieldNames = new ArrayList<String>();
    List<Type> constructorInputs = new ArrayList<Type>();
    for (StructField field: structType.fields()) {
      fieldNames.add(field.name());
      constructorInputs.add(field.type());
    }

    FunctionType constructorType =
        new FunctionType(constructorInputs, type.asList(), false);

    return structConstructor(func, posArgs, fieldNames, constructorType);
  }


  private static FunctionCall subtypeConstructorFromAST(Context context,
      List<SwiftAST> annotations, String func, List<SwiftAST> posArgs,
      Map<String, SwiftAST> kwArgs, Type type)
          throws InvalidAnnotationException, InvalidSyntaxException {
    assert(Types.isSubType(type));

    if (kwArgs.size() > 0) {
      throw new InvalidSyntaxException(context,
          "Don't support keyword args for subtype constructors");
    }

    if (annotations.size() > 0) {
      throw new InvalidAnnotationException(context, "Do not support "
          + "annotations for subtype constructor (call to " + func + ")");
    }

    Type baseType = type.stripSubTypes();
    FunctionType constructorType = new FunctionType(baseType.asList(),
                                                type.asList(), false);

    return subtypeConstructor(func, posArgs, constructorType);
  }

}
