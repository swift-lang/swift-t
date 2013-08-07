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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class FunctionCall {
  private final String function;
  private final List<SwiftAST> args;
  private final FunctionType type;
  private final Map<TaskPropKey, SwiftAST> annotationExprs;
  
  private FunctionCall(String function, List<SwiftAST> args,
                       FunctionType type, Map<TaskPropKey, SwiftAST> annotationExprs) {
    super();
    this.function = function;
    this.args = args;
    this.type = type;
    this.annotationExprs = annotationExprs;
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

  private static TaskPropKey getPropKey(Context context, SwiftAST tag)
        throws InvalidAnnotationException {
    assert(tag.getType() == ExMParser.ID);
    String annotName = tag.getText();
    if (annotName.equals(Annotations.FNCALL_PAR)) {
      return  TaskPropKey.PARALLELISM;
    } else if (annotName.equals(Annotations.FNCALL_PRIO)) {
      return  TaskPropKey.PRIORITY;
    } else if (annotName.equals(Annotations.FNCALL_LOCATION)) {
      return TaskPropKey.LOCATION;
    } else {
      throw new InvalidAnnotationException(context, "function call",
                                           annotName, false);
    }
  }

  public static FunctionCall fromAST(Context context, SwiftAST tree,
          boolean doWarn) throws UndefinedFunctionException, InvalidAnnotationException {
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
    
    FunctionType ftype = context.lookupFunction(f);
    if (ftype == null) {
      throw UndefinedFunctionException.unknownFunction(context, f);
    }
    
    Map<TaskPropKey, SwiftAST> annotations = new TreeMap<TaskPropKey, SwiftAST>();
    for (SwiftAST annTree: tree.children(2)) {
      assert(annTree.getType() == ExMParser.CALL_ANNOTATION);
      assert(annTree.getChildCount() == 2);
      SwiftAST tag = annTree.child(0);
      SwiftAST expr = annTree.child(1);
      TaskPropKey propKey = getPropKey(context, tag);
      annotations.put(propKey, expr);
    }
    
    return new FunctionCall(f, arglist.children(), ftype, annotations);
  }

}
