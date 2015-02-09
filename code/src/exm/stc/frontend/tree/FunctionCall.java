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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Unimplemented;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context;
import exm.stc.frontend.Context.DefInfo;
import exm.stc.frontend.Context.DefKind;
import exm.stc.frontend.LogHelper;

public class FunctionCall {
  public static enum FunctionCallKind {
    REGULAR_FUNCTION,
    STRUCT_CONSTRUCTOR,
  }

  private final FunctionCallKind kind;
  private final String originalName;
  private final List<Pair<FnID, FunctionType>> overloads;
  private final List<SwiftAST> args;
  private final Map<TaskPropKey, SwiftAST> annotationExprs;
  private final boolean softLocation;

  private FunctionCall(FunctionCallKind kind, String originalName,
      List<Pair<FnID, FunctionType>> overloads, List<SwiftAST> args,
      Map<TaskPropKey, SwiftAST> annotationExprs, boolean softLocation) {
    this.kind = kind;
    this.originalName = originalName;
    this.overloads = overloads;
    this.args = args;
    this.annotationExprs = annotationExprs;
    this.softLocation = softLocation;
  }

  private static FunctionCall regularFunctionCall(String originalName,
      List<Pair<FnID, FunctionType>> overloads, SwiftAST arglist,
      Map<TaskPropKey, SwiftAST> annotations, boolean softLocation) {
    return new FunctionCall(FunctionCallKind.REGULAR_FUNCTION, originalName,
                  overloads, arglist.children(), annotations, softLocation);
  }

  private static FunctionCall structConstructor(String typeName,
      SwiftAST arglist, FunctionType ftype) {
    assert(ftype.getOutputs().size() == 1 &&
        Types.isStruct(ftype.getOutputs().get(0)));

    Pair<FnID, FunctionType> fn = Pair.create(constructorID(typeName), ftype);

    return new FunctionCall(FunctionCallKind.STRUCT_CONSTRUCTOR, typeName,
        fn.asList(), arglist.children(),
        Collections.<TaskPropKey,SwiftAST>emptyMap(), false);
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

  public List<Pair<FnID, FunctionType>> overloads() {
    return overloads;
  }

  public List<SwiftAST> args() {
    return args;
  }

  public Map<TaskPropKey, SwiftAST> annotations() {
    return Collections.unmodifiableMap(annotationExprs);
  }

  /**
   * @return whether the location should be interpreted as a soft location
   */
  public boolean softLocation() {
    return softLocation;
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

    DefInfo def = context.lookupDef(f);
    List<SwiftAST> annotations = tree.children(2);

    if (def == null) {
      throw UndefinedFunctionException.unknownFunction(context, f);
    } else if (def.kind == DefKind.FUNCTION) {
      FunctionType ftype = context.lookupFunction(f);
      assert(ftype != null);
      return regularFunctionFromAST(context, annotations, f, arglist, ftype);
    } else if (def.kind == DefKind.TYPE) {
      Type type = context.lookupTypeUnsafe(f);
      assert(type != null);
      if (Types.isStruct(type)) {
        return structConstructorFromAST(context, annotations, f, arglist, type);
      }
    }
    throw new TypeMismatchException(f + " is not a function and "
                                    + "cannot be called");
  }

  private static FunctionCall regularFunctionFromAST(Context context,
      List<SwiftAST> annotationTs, String f, SwiftAST arglist, FunctionType ftype)
      throws UserException, InvalidAnnotationException {
    Map<TaskPropKey, SwiftAST> annotations = new TreeMap<TaskPropKey, SwiftAST>();
    boolean softLocation = false;
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
      } else if (annotName.equals(Annotations.FNCALL_LOCATION)) {
        putAnnotationNoDupes(context, annotations, TaskPropKey.LOCATION, expr);
      } else if (annotName.equals(Annotations.FNCALL_SOFT_LOCATION)) {
        putAnnotationNoDupes(context, annotations, TaskPropKey.LOCATION, expr);
        softLocation = true;
      } else {
        throw new InvalidAnnotationException(context, "function call",
                                             annotName, false);
      }
    }

    // TODO: lookup overloads here
    FnID id = Unimplemented.makeFunctionID(f);
    Pair<FnID, FunctionType> fn = Pair.create(id, ftype);

    return regularFunctionCall(f, fn.asList(), arglist, annotations,
                               softLocation);
  }

  private static void putAnnotationNoDupes(Context context,
      Map<TaskPropKey, SwiftAST> annotations, TaskPropKey key, SwiftAST expr)
          throws UserException {
    SwiftAST prev = annotations.put(key, expr);
    if (prev != null) {
      throw new UserException(context, "Duplicate function call annotation: " +
                    key.toString().toLowerCase() + " defined multiple times");
    }
  }

  private static FunctionCall structConstructorFromAST(Context context,
      List<SwiftAST> annotations, String func, SwiftAST arglist, Type type)
          throws InvalidAnnotationException {
    assert(Types.isStruct(type));

    if (annotations.size() > 0) {
      throw new InvalidAnnotationException(context, "Do not support "
          + "annotations for struct constructor (call to " + func + ")");
    }

    StructType structType = (StructType)type.getImplType();
    List<Type> constructorInputs = new ArrayList<Type>();
    for (StructField field: structType.fields()) {
      constructorInputs.add(field.type());
    }

    FunctionType constructorType = new FunctionType(constructorInputs, type.asList(), false);

    return structConstructor(func, arglist, constructorType);
  }

}
