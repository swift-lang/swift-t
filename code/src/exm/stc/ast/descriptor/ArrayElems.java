package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class ArrayElems {
  
  private final ArrayList<SwiftAST> members;
  
  
  public List<SwiftAST> getMembers() {
    return Collections.unmodifiableList(members);
  }
  
  public int getMemberCount() {
    return members.size();
  }

  public SwiftAST getMember(int i) {
    return members.get(i);
  }
  
  public ArrayElems(ArrayList<SwiftAST> members) {
    super();
    this.members = members;
  }
  
  public static ArrayElems fromAST(Context context, SwiftAST tree) {
    assert(tree.getType() == ExMParser.ARRAY_ELEMS);
    ArrayList<SwiftAST> members = new ArrayList<SwiftAST>(tree.getChildCount());
    for (SwiftAST child: tree.children()) {
      members.add(child);
    }
    return new ArrayElems(members);
  }

  /**
   * @return the type of the result
   * @throws UserException 
   */
  public ExprType getType(Context context) throws UserException {
    // Check to see all arguments have compatible types
    List<SwiftAST> members = getMembers();
    if (members.size() == 0) {
      throw new STCRuntimeError("Empty array constructor, " +
          "compiler doesn't yet know how to infer type");
    }
    List<SwiftType> memberTypes = new ArrayList<SwiftType>(members.size());
    for (SwiftAST elem: members) {
      memberTypes.add(TypeChecker.findSingleExprType(context, elem));
    }
    
    Set<SwiftType> possibleTypes = TypeChecker.typeIntersection(memberTypes);
    if (possibleTypes.size() == 0) {
      throw new TypeMismatchException(context, "Elements in array" +
          " constructor have incompatible types: " + memberTypes.toString());
    }
    
    List<SwiftType> possibleArrayTypes = new ArrayList<SwiftType>();
    for (SwiftType alt: possibleTypes) {
      possibleArrayTypes.add(new ArrayType(alt));
    }
    return new ExprType(UnionType.createUnionType(possibleArrayTypes));
  }
}
