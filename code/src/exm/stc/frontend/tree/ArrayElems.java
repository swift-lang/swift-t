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
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Types.WildcardType;
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
      return new ExprType(new ArrayType(Types.F_INT, new WildcardType()));
    }
    List<Type> memberTypes = new ArrayList<Type>(members.size());
    for (SwiftAST elem: members) {
      memberTypes.add(TypeChecker.findSingleExprType(context, elem));
    }
    
    List<Type> possibleTypes = Types.typeIntersection(memberTypes);
    if (possibleTypes.size() == 0) {
      throw new TypeMismatchException(context, "Elements in array" +
          " constructor have incompatible types: " + memberTypes.toString());
    }
    
    List<Type> possibleArrayTypes = new ArrayList<Type>();
    for (Type alt: possibleTypes) {
      possibleArrayTypes.add(new ArrayType(Types.F_INT, alt));
    }
    return new ExprType(UnionType.makeUnion(possibleArrayTypes));
  }
}
