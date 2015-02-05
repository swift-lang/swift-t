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
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;
import exm.stc.frontend.Context.DefInfo;
import exm.stc.frontend.Context.DefKind;
import exm.stc.frontend.LogHelper;

public class FunctionCall {
  public static enum FunctionCallKind {
    REGULAR_FUNCTION,
    STRUCT_CONSTRUCTOR,
    SUBTYPE_CONSTRUCTOR,
  }

  private final FunctionCallKind kind;
  private final String function;
  private final List<SwiftAST> args;
  private final FunctionType type;
  private final Map<TaskPropKey, SwiftAST> annotationExprs;
  // Location (as location struct type) - null if unspecified
  private final SwiftAST location;
  // Override strictness of location (to support @soft_location annotation)
  private final boolean softLocationOverride;

  private FunctionCall(FunctionCallKind kind, String function,
      List<SwiftAST> args, FunctionType type,
      Map<TaskPropKey, SwiftAST> annotationExprs,
      SwiftAST location, boolean softLocationOverride) {
    this.kind = kind;
    this.function = function;
    this.args = args;
    this.type = type;
    this.annotationExprs = annotationExprs;
    this.location = location;
    this.softLocationOverride = softLocationOverride;
  }

  private static FunctionCall regularFunctionCall(String f, SwiftAST arglist,
      FunctionType ftype, Map<TaskPropKey, SwiftAST> annotations,
      SwiftAST location, boolean softLocationOverride) {
    return new FunctionCall(FunctionCallKind.REGULAR_FUNCTION, f,
            arglist.children(), ftype, annotations, location, softLocationOverride);
  }

  private static FunctionCall structConstructor(String f, SwiftAST arglist,
                                                FunctionType ftype) {
    assert(ftype.getOutputs().size() == 1 &&
        Types.isStruct(ftype.getOutputs().get(0)));
    return new FunctionCall(FunctionCallKind.STRUCT_CONSTRUCTOR, f, arglist.children(),
                ftype, Collections.<TaskPropKey,SwiftAST>emptyMap(), null, false);
  }

  private static FunctionCall subtypeConstructor(String f, SwiftAST arglist,
	      FunctionType ftype) {
	    assert(ftype.getOutputs().size() == 1 &&
	        Types.isSubType(ftype.getOutputs().get(0)));
	    return new FunctionCall(FunctionCallKind.SUBTYPE_CONSTRUCTOR, f,
	        arglist.children(), ftype, Collections.<TaskPropKey,SwiftAST>emptyMap(),
	        null, false);
	  }

  public FunctionCallKind kind() {
    return kind;
  }

  public String function() {
    return function;
  }

  public List<SwiftAST> args() {
    return args;
  }

  public FunctionType type() {
    return type;
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
      } else if (Types.isSubType(type)) {
        return subtypeConstructorFromAST(context, annotations, f, arglist, type);
      }
    }
    throw new TypeMismatchException(f + " is not a function and "
                                    + "cannot be called");
  }

  private static FunctionCall regularFunctionFromAST(Context context,
      List<SwiftAST> annotationTs, String f, SwiftAST arglist, FunctionType ftype)
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

        if (annotName.equals(Annotations.FNCALL_SOFT_LOCATION)) {
          softLocationOverride = true;
        }
      } else {
        throw new InvalidAnnotationException(context, "function call",
                                             annotName, false);
      }
    }

    return regularFunctionCall(f, arglist, ftype, annotations, location,
                               softLocationOverride);
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


  private static FunctionCall subtypeConstructorFromAST(Context context,
      List<SwiftAST> annotations, String func, SwiftAST arglist, Type type)
          throws InvalidAnnotationException {
    assert(Types.isSubType(type));

    if (annotations.size() > 0) {
      throw new InvalidAnnotationException(context, "Do not support "
          + "annotations for subtype constructor (call to " + func + ")");
    }

    Type baseType = type.stripSubTypes();
    FunctionType constructorType = new FunctionType(baseType.asList(),
    											type.asList(), false);

    return subtypeConstructor(func, arglist, constructorType);
  }

}
