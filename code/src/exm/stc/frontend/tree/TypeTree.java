package exm.stc.frontend.tree;

import java.util.ArrayList;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.WildcardType;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class TypeTree {

  /**
   * Extract a type from a subtree corresponding to a type_prefix rule
   * in the grammar
   * @param context
   * @param typeT
   * @return
   * @throws UndefinedTypeException
   * @throws TypeMismatchException 
   */
  public static Type extractTypePrefix(Context context, SwiftAST typeT)
                      throws UndefinedTypeException, TypeMismatchException {
    switch (typeT.getType()) {
      case ExMParser.ID:
        return context.lookupTypeUser(typeT.getText());
      case ExMParser.PARAM_TYPE: {
        assert(typeT.childCount() == 2);
        SwiftAST outerTypeT = typeT.child(0);
        SwiftAST paramT = typeT.child(1);
        Type param = extractStandaloneType(context, paramT);

        Type outerType = extractParameterizableType(context, outerTypeT, param);
        String paramTypeStr = outerType.typeName() + "<" + param.typeName() + ">";
        throw new STCRuntimeError("TODO: param type " + paramTypeStr);
      }
      default:
        throw new STCRuntimeError("Unexpected token in type: " +
                              LogHelper.tokName(typeT.getType()));
    }
  }

  public static Type extractStandaloneType(Context context,
      SwiftAST standaloneTypeT) throws UndefinedTypeException, TypeMismatchException {
    assert(standaloneTypeT.getType() == ExMParser.STANDALONE_TYPE);
    assert(standaloneTypeT.getChildCount() >= 1);
    // Extract the initial type
    String resultTypeName = standaloneTypeT.child(0).getText(); 
    Type beforeArrayType = context.lookupTypeUser(resultTypeName);
    
    return applyArrayMarkers(context, standaloneTypeT.children(1), 
                                       beforeArrayType);
  }

  public static Type applyArrayMarkers(Context context, 
      List<SwiftAST> markers, Type initType) throws UndefinedTypeException,
      TypeMismatchException {
    Type resultType = initType;
    // Apply the array markers from right to left
    for (int i = markers.size() - 1; i >= 0; i--) {
      SwiftAST arrayT = markers.get(i);
      assert(arrayT.getType() == ExMParser.ARRAY);
      Type keyType = getArrayKeyType(context, arrayT);
      resultType = new ArrayType(keyType, resultType);
    }
    return resultType;
  }

  /**
   * Given an ARRAY tree, corresponding to an array type specification,
   * e.g. [] or [string], then decide on the key type of the array 
   * @param context
   * @param subtree
   * @return
   * @throws UndefinedTypeException
   * @throws TypeMismatchException 
   */
  public static Type getArrayKeyType(Context context, SwiftAST subtree)
      throws UndefinedTypeException, TypeMismatchException {
    assert(subtree.getType() == ExMParser.ARRAY);
    Type keyType;
    if (subtree.getChildCount() == 0) {
      // Default to int key type if not specified.
      keyType = Types.F_INT;
    } else {
      assert(subtree.getChildCount() == 1);
      SwiftAST standaloneTypeT = subtree.child(0);
      keyType = extractStandaloneType(context, standaloneTypeT);
      if (!Types.isValidArrayKey(keyType)) {
        throw new TypeMismatchException(context, "Unsupported key type for"
                                    + " arrays: " + keyType);
      }
    }
    return keyType;
  }

  /**
   * Extract a list of types from a multi_type rule in the grammar.
   * @param context
   * @param baseTypes
   * @return A list with at least one type
   * @throws UndefinedTypeException
   * @throws TypeMismatchException 
   */
  public static List<Type> extractMultiType(Context context, SwiftAST baseTypes)
      throws UndefinedTypeException, TypeMismatchException {
    assert(baseTypes.getType() == ExMParser.MULTI_TYPE);
    assert(baseTypes.getChildCount() >= 1); // Grammar should ensure this
    List<Type> altPrefixes = new ArrayList<Type>(baseTypes.getChildCount());
    
    for (int i = 0; i < baseTypes.getChildCount(); i++) {
      SwiftAST typeAlt = baseTypes.child(i);
      altPrefixes.add(TypeTree.extractTypePrefix(context, typeAlt));
    }
    return altPrefixes;
  }
  
  /**
   * Look up and fill in parameters for a parameterizable type
   * @param typeT
   * @param paramT
   * @return
   * @throws TypeMismatchException
   */
  public static Type extractParameterizableType(Context context,
          SwiftAST typeT, Type paramT) throws TypeMismatchException {
    assert(typeT.getType() == ExMParser.ID);
    String typeName = typeT.getText();
    // TODO: actual logic
    if (typeName.equals("bag")) {
      return new WildcardType();
    } else {
      throw new TypeMismatchException(typeName + 
           " is not the name of a type that can accept a <...> parameter");
    }
  }

}
