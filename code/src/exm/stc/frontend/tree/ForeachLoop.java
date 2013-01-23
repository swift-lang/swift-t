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

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.frontend.Context;
import exm.stc.frontend.LocalContext;
import exm.stc.frontend.TypeChecker;

public class ForeachLoop {
  private static final int DEFAULT_SPLIT_DEGREE = 16;
  private final SwiftAST arrayVarTree;
  private final SwiftAST loopBodyTree;
  private final String memberVarName;
  private final String loopCountVarName;

  private LocalContext loopBodyContext = null;
  private Var loopCountVal = null;
  private Var memberVar = null;
  private final ArrayList<String> annotations;
  private int unroll = 1;
  private int splitDegree = DEFAULT_SPLIT_DEGREE;

  public int getDesiredUnroll() {
    return unroll;
  }

  public int getSplitDegree() {
    return splitDegree;
  }
  
  public List<String> getAnnotations() {
    return Collections.unmodifiableList(annotations);
  }

  public boolean isSyncLoop() {
    return !annotations.contains(Annotations.LOOP_ASYNC);
  }

  public Var getMemberVar() {
    return memberVar;
  }

  public Var getLoopCountVal() {
    return loopCountVal;
  }

  public SwiftAST getArrayVarTree() {
    return arrayVarTree;
  }

  public SwiftAST getBody() {
    return loopBodyTree;
  }

  public String getMemberVarName() {
    return memberVarName;
  }

  public String getCountVarName() {
    return loopCountVarName;
  }

  public LocalContext getBodyContext() {
    return loopBodyContext;
  }

  private ForeachLoop(SwiftAST arrayVarTree, SwiftAST loopBodyTree,
      String memberVarName, String loopCountVarName,
      ArrayList<String> annotations) {
    super();
    this.arrayVarTree = arrayVarTree;
    this.loopBodyTree = loopBodyTree;
    this.memberVarName = memberVarName;
    this.loopCountVarName = loopCountVarName;
    this.annotations = annotations;
  }

  public static ForeachLoop fromAST(Context context, SwiftAST tree) 
      throws UserException {
    assert (tree.getType() == ExMParser.FOREACH_LOOP);

    ArrayList<String> annotations = new ArrayList<String>();

    // How many times to unroll loop (1 == don't unroll)
    int unrollFactor = 1;
    int splitDegree = DEFAULT_SPLIT_DEGREE;

    
    int annotationCount = 0;
    for (int i = tree.getChildCount() - 1; i >= 0; i--) {
      SwiftAST subtree = tree.child(i);
      if (subtree.getType() == ExMParser.ANNOTATION) {
        if (subtree.getChildCount() == 2) {
          String key = subtree.child(0).getText();
          if (key.equals(Annotations.LOOP_UNROLL)
              || key.equals(Annotations.LOOP_SPLIT_DEGREE)) {
            boolean posint = false;
            if (subtree.child(1).getType() == ExMParser.NUMBER) {
              int val = Integer.parseInt(subtree.child(1).getText());
              if (val > 0) {
                posint = true;
                if (key.equals(Annotations.LOOP_UNROLL)) {
                  unrollFactor = val;
                } else {
                  splitDegree = val;
                }
                annotationCount++;
              }
            }
            if (!posint) {
              throw new InvalidAnnotationException(context, "Expected value "
                  + "of " + key + " to be a positive integer");
            }
          } else {
            throw new InvalidAnnotationException(context, key
                + " is not the name of a key-value annotation for "
                + " foreach loops");
          }
        } else {
          assert (subtree.getChildCount() == 1);
          annotations.add(subtree.child(0).getText());
          annotationCount++;
        }
      } else {
        break;
      }
    }
    
    if (annotations.contains(Annotations.LOOP_NOSPLIT)) {
      // Disable splitting
      splitDegree = -1;
    }

    int childCount = tree.getChildCount() - annotationCount;
    assert(childCount == 3 || childCount == 4);
    
    SwiftAST arrayVarTree = tree.child(0);
    SwiftAST loopBodyTree = tree.child(1);
    SwiftAST memberVarTree = tree.child(2);
    assert (memberVarTree.getType() == ExMParser.ID);
    String memberVarName = memberVarTree.getText();
    
    context.checkNotDefined(memberVarName);

    String loopCountVarName;
    if (childCount == 4 && tree.child(3).getType() != ExMParser.ANNOTATION) {
      SwiftAST loopCountTree = tree.child(3);
      assert (loopCountTree.getType() == ExMParser.ID);
      loopCountVarName = loopCountTree.getText();
      context.checkNotDefined(loopCountVarName);
    } else {
      loopCountVarName = null;
    }
    ForeachLoop loop = new ForeachLoop(arrayVarTree, loopBodyTree,
        memberVarName, loopCountVarName, annotations);
    loop.validateAnnotations(context);
    loop.unroll = unrollFactor;
    loop.splitDegree = splitDegree;
    return loop;
  }

  private void validateAnnotations(Context context)
      throws InvalidAnnotationException {
    for (String annotation: annotations) {
      if (!annotation.equals(Annotations.LOOP_SYNC) &&
          !annotation.equals(Annotations.LOOP_ASYNC) &&
          !annotation.equals(Annotations.LOOP_NOSPLIT)) {
        throw new InvalidAnnotationException(context, "Invalid loop"
                + "annotation @" + annotation);
      }
    }
    if (annotations.contains(Annotations.LOOP_SYNC) &&
        annotations.contains(Annotations.LOOP_ASYNC)) {
      throw new InvalidAnnotationException(context, "Contradictory "
        + " loop annotations");
    }
  }

  /**
   * returns true for the special case of foreach where we're iterating over a
   * range bounded by integer values e.g. foreach x in [1:10] { ... } or foreach
   * x, i in [f():g():h()] { ... }
   * 
   * @return true if it is a range foreach loop
   */
  public boolean iteratesOverRange() {
    return arrayVarTree.getType() == ExMParser.ARRAY_RANGE;
  }

  public Type findArrayType(Context context)
      throws UserException {
    Type arrayType = TypeChecker.findSingleExprType(context, arrayVarTree);
    for (Type alt: UnionType.getAlternatives(arrayType)) {
      if (Types.isArray(alt) || Types.isArrayRef(alt)) {
        return alt;
      }
    }
    throw new TypeMismatchException(context,
        "Expected array type in expression for foreach loop " +
                " but got type: " + arrayType.typeName());
  }

  /**
   * Initialize the context and define the two variables: for array member and
   * (optionally) for the loop count
   * 
   * @param context
   * @return
   * @throws UserException
   */
  public Context setupLoopBodyContext(Context context)
      throws UserException {
    // Set up the context for the loop body with loop variables
    loopBodyContext = new LocalContext(context);
    if (loopCountVarName != null) {
      loopCountVal = context.createLocalValueVariable(Types.V_INT,
          loopCountVarName);
    } else {
      loopCountVal = null;
    }

    Type arrayType = findArrayType(context);
    memberVar = loopBodyContext.declareVariable(
        Types.getArrayMemberType(arrayType), getMemberVarName(),
        VarStorage.STACK, DefType.LOCAL_USER, null);
    return loopBodyContext;
  }
}
