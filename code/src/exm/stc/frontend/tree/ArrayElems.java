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
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Types.WildcardType;
import exm.stc.frontend.Context;
import exm.stc.frontend.typecheck.TypeChecker;

public class ArrayElems {

  // Keys. null if keys not specified
  private final ArrayList<SwiftAST> keys;
  private final ArrayList<SwiftAST> values;

  public boolean hasKeys() {
    return keys != null;
  }

  public List<SwiftAST> getKeys() {
    assert(keys != null) : "No keys, should call hasKeys first";
    return Collections.unmodifiableList(keys);
  }

  public List<SwiftAST> getVals() {
    return Collections.unmodifiableList(values);
  }

  public int getElemCount() {
    return values.size();
  }

  public SwiftAST getVal(int i) {
    return values.get(i);
  }

  /**
   * Constructor when keys not specified.
   * @param values
   */
  public ArrayElems(ArrayList<SwiftAST> values) {
    this (null, values);
  }

  public ArrayElems(ArrayList<SwiftAST> keys, ArrayList<SwiftAST> values) {
    this.keys = keys;
    this.values = values;
  }

  public static ArrayElems fromAST(Context context, SwiftAST tree) {
    if (tree.getType() == ExMParser.ARRAY_ELEMS) {
      ArrayList<SwiftAST> vals = new ArrayList<SwiftAST>(tree.getChildCount());
      for (SwiftAST child: tree.children()) {
        vals.add(child);
      }
      return new ArrayElems(vals);
    } else {
      assert(tree.getType() == ExMParser.ARRAY_KV_ELEMS);
      ArrayList<SwiftAST> keys = new ArrayList<SwiftAST>(tree.getChildCount());
      ArrayList<SwiftAST> vals = new ArrayList<SwiftAST>(tree.getChildCount());
      for (SwiftAST elemT: tree.children()) {
        assert(elemT.getType() == ExMParser.ARRAY_KV_ELEM);
        assert(elemT.getChildCount() == 2);
        keys.add(elemT.child(0));
        vals.add(elemT.child(1));
      }
      return new ArrayElems(keys, vals);
    }
  }

  /**
   * @return the type of the result
   * @throws UserException
   */
  public Type getType(Context context) throws UserException {
    // Check to see all arguments have compatible types
    List<SwiftAST> vals = getVals();

    List<Type> possibleKeyTypes;
    List<Type> possibleValTypes = findCompatibleTypes(context, vals);
    if (keys == null) {
      possibleKeyTypes = Collections.singletonList(Types.F_INT);
    } else {
      possibleKeyTypes = findCompatibleTypes(context, keys);
      checkKeyTypeAlts(context, possibleKeyTypes);
    }

    List<Type> possibleArrayTypes = new ArrayList<Type>();
    for (Type keyAlt: possibleKeyTypes) {
      if (Types.isValidArrayKey(keyAlt)) {
        for (Type valAlt: possibleValTypes) {
          possibleArrayTypes.add(ArrayType.sharedArray(keyAlt, valAlt));
        }
      }
    }
    return UnionType.makeUnion(possibleArrayTypes);
  }

  /**
   * Check that at least one key type is viable
   * @param context
   * @param possibleKeyTypes
   * @throws TypeMismatchException
   */
  private void checkKeyTypeAlts(Context context, List<Type> possibleKeyTypes)
      throws TypeMismatchException {
    boolean foundValidKeyType = false;
    for (Type possibleKeyType: possibleKeyTypes) {
      if (Types.isValidArrayKey(possibleKeyType)) {
        foundValidKeyType = true;
      }
    }
    if (!foundValidKeyType) {
      throw new TypeMismatchException(context, "Invalid key type in array " +
              "literal: " + UnionType.makeUnion(possibleKeyTypes).typeName());
    }
  }

  private List<Type> findCompatibleTypes(Context context, List<SwiftAST> exprs)
      throws UserException, TypeMismatchException {

    if (exprs.size() == 0) {
      return Collections.<Type>singletonList(new WildcardType());
    } else {
      List<Type> valTypes = new ArrayList<Type>(exprs.size());
      for (SwiftAST elem: exprs) {
        valTypes.add(TypeChecker.findExprType(context, elem));
      }

      List<Type> possibleTypes = Types.typeIntersection(valTypes);
      if (possibleTypes.size() == 0) {
        throw new TypeMismatchException(context, "Elements in array" +
            " constructor have incompatible types: " + valTypes.toString());
      }
      return possibleTypes;
    }
  }
}
