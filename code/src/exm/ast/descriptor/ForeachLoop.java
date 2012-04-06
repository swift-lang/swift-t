package exm.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.ast.Context;
import exm.ast.LocalContext;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.ast.Variable;
import exm.ast.Types.SwiftType;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.antlr.ExMParser;
import exm.parser.util.DoubleDefineException;
import exm.parser.util.InvalidAnnotationException;
import exm.parser.util.ParserRuntimeException;
import exm.parser.util.TypeMismatchException;
import exm.parser.util.UserException;

public class ForeachLoop {
  private static final String UNROLL_ANNOTATION = "unroll";
  private static final String SPLIT_DEGREE_ANNOTATION = "splitdegree";
  private static final String SYNC_ANNOTATION_NAME = "sync";
  private final SwiftAST arrayVarTree;
  private final SwiftAST loopBodyTree;
  private final String memberVarName;
  private final String loopCountVarName;

  private LocalContext loopBodyContext = null;
  private Variable loopCountVal = null;
  private Variable memberVar = null;
  private final ArrayList<String> annotations;
  private int unroll = 1;
  private int splitDegree = -1;

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
    return annotations.contains(SYNC_ANNOTATION_NAME);
  }

  public Variable getMemberVar() {
    return memberVar;
  }

  public Variable getLoopCountVal() {
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

  public static ForeachLoop fromAST(Context context, SwiftAST tree,
      TypeChecker typecheck) throws UserException {
    assert (tree.getType() == ExMParser.FOREACH_LOOP);

    ArrayList<String> annotations = new ArrayList<String>();

    // How many times to unroll loop (1 == don't unroll)
    int unrollFactor = 1;
    int splitDegree = -1; // Don't split by default

    
    int annotationCount = 0;
    for (int i = tree.getChildCount() - 1; i >= 0; i--) {
      SwiftAST subtree = tree.child(i);
      if (subtree.getType() == ExMParser.ANNOTATION) {
        if (subtree.getChildCount() == 2) {
          String key = subtree.child(0).getText();
          if (key.equals(UNROLL_ANNOTATION)
              || key.equals(SPLIT_DEGREE_ANNOTATION)) {
            boolean posint = false;
            if (subtree.child(1).getType() == ExMParser.NUMBER) {
              int val = Integer.parseInt(subtree.child(1).getText());
              if (val > 0) {
                posint = true;
                if (key.equals(UNROLL_ANNOTATION)) {
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

    int childCount = tree.getChildCount() - annotationCount;
    assert(childCount == 3 || childCount == 4);
    
    SwiftAST arrayVarTree = tree.child(0);
    SwiftAST loopBodyTree = tree.child(1);
    SwiftAST memberVarTree = tree.child(2);
    assert (memberVarTree.getType() == ExMParser.ID);
    String memberVarName = memberVarTree.getText();
    if (context.getDeclaredVariable(memberVarName) != null) {
      throw new DoubleDefineException(context, "Variable " + memberVarName
          + " already defined");
    }

    String loopCountVarName;

    if (childCount == 4 && tree.child(3).getType() != ExMParser.ANNOTATION) {
      SwiftAST loopCountTree = tree.child(3);
      assert (loopCountTree.getType() == ExMParser.ID);
      loopCountVarName = loopCountTree.getText();
      if (context.getDeclaredVariable(loopCountVarName) != null) {
        throw new DoubleDefineException(context, "Variable " + loopCountVarName
            + " already defined");
      }

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
    if (annotations.size() > 1) {
      throw new InvalidAnnotationException(context, "Too many annotations "
          + " on foreach list: " + annotations.toString()
          + ", only expected one");
    } else if (annotations.size() == 1) {
      if (!annotations.get(0).equals(SYNC_ANNOTATION_NAME)) {
        throw new ParserRuntimeException("Unknown loop annotation "
            + annotations.get(0));
      }
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

  public SwiftType findArrayType(Context context, TypeChecker typecheck)
      throws UserException {
    SwiftType arrayType = typecheck.findSingleExprType(context, arrayVarTree);
    if (!Types.isArray(arrayType) && !Types.isArrayRef(arrayType)) {
      throw new TypeMismatchException(context,
          "Expected array type in expression for foreach loop");
    }
    return arrayType;
  }

  /**
   * Initialize the context and define the two variables: for array member and
   * (optionally) for the loop count
   * 
   * @param context
   * @param typecheck
   * @return
   * @throws UserException
   */
  public Context setupLoopBodyContext(Context context, TypeChecker typecheck)
      throws UserException {
    // Set up the context for the loop body with loop variables
    loopBodyContext = new LocalContext(context);
    if (loopCountVarName != null) {
      loopCountVal = context.createLocalValueVariable(Types.VALUE_INTEGER,
          loopCountVarName);
    } else {
      loopCountVal = null;
    }

    SwiftType arrayType = findArrayType(context, typecheck);
    memberVar = loopBodyContext.declareVariable(
        Types.getArrayMemberType(arrayType), getMemberVarName(),
        VariableStorage.STACK, DefType.LOCAL_USER, null);
    return loopBodyContext;
  }
}
