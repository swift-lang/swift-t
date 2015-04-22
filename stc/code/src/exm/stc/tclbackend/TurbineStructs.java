package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.util.Pair;
import exm.stc.tclbackend.tree.Dict;
import exm.stc.tclbackend.tree.Expression;

/**
 * Module to track different struct types we're using in generated code
 */
public class TurbineStructs {

  /**
   * Struct types after system reserved.
   * TODO: better system to get this from Turbine
   */
  private static final int MIN_USER_STRUCT_TYPE = 16;

  private int nextStructType = MIN_USER_STRUCT_TYPE;
  private List<Pair<Integer, StructType>> structTypes =
                      new ArrayList<Pair<Integer, StructType>>();
  private Map<StructType, Integer> structTypeIds =
                      new HashMap<StructType, Integer>();

  public void newType(StructType st) {
    structTypes.add(Pair.create(nextStructType, st));
    structTypeIds.put(st, nextStructType);
    nextStructType++;
  }

  public List<Pair<Integer, StructType>> getTypeList() {
    return Collections.unmodifiableList(structTypes);
  }

  public int getIDForType(StructType type) {
    Integer id = structTypeIds.get(type);
    assert(id != null): "Expected id to exist for type: " + type;
    return id;
  }

  /**
   * @param structType
   * @param structField
   * @return
   */
  public int getFieldID(StructType structType, String structField) {
    List<StructField> fields = structType.fields();
    for (int i = 0; i < fields.size(); i++) {
      StructField f = fields.get(i);
      if (structField.equals(f.name()))
        return i;
    }
    throw new STCRuntimeError("Field not found in type: " + structField +
                              " " + structType);
  }

  /**
   * Convert a flat representation of nested dict into the appropriate
   * Tcl dictionary expression
   * @param fields
   * @return
   */
  public static Dict buildNestedDict(List<Pair<List<String>, Arg>> fields) {
    // Map of prefix => (rest of path, val)
    ListMultimap<String, Pair<List<String>, Arg>> grouped =
            ArrayListMultimap.create();

    // Group together common prefixes (i.e. structs inside structs)
    for (Pair<List<String>, Arg> field: fields) {
      List<String> fieldPath = field.val1;
      assert(fieldPath.size() > 0);
      Arg fieldVal = field.val2;

      List<String> pathTail = fieldPath.subList(1, fieldPath.size());
      grouped.put(fieldPath.get(0), Pair.create(pathTail, fieldVal));
    }

    List<Pair<String, Expression>> dictPairs =
            new ArrayList<Pair<String, Expression>>();

    for (Entry<String, Collection<Pair<List<String>, Arg>>> e:
           grouped.asMap().entrySet()) {
      String field = e.getKey();
      List<Pair<List<String>, Arg>> vals =
          (List<Pair<List<String>, Arg>>) e.getValue();

      Expression fieldExpr;
      if (vals.size() == 1 && vals.get(0).val1.isEmpty()) {
        // This is a single field value
        Arg fieldVal = vals.get(0).val2;
        fieldExpr = TclUtil.argToExpr(fieldVal);
      } else {
        // Build sub-dictionary
        fieldExpr = buildNestedDict(vals);
      }

      dictPairs.add(Pair.create(field, fieldExpr));
    }

    // Don't bother checking for dupes, we have eliminated them already
    return Dict.dictCreateSE(false, dictPairs);
  }

}
