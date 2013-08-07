package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.util.Pair;

/**
 * Module to track different struct types we're using in generated code
 */
public class TurbineStructs {

  private int nextStructType = 0;
  private List<Pair<Integer, StructType>> structTypes =
                      new ArrayList<Pair<Integer, StructType>>();
  private Map<StructType, Integer> structTypeIds =
                      new HashMap<StructType, Integer>();
  private Map<StructType, List<Pair<String, Type>>> fieldList =
                      new HashMap<StructType, List<Pair<String, Type>>>();
  
  public void newType(StructType st) {
    structTypes.add(Pair.create(nextStructType, st));
    structTypeIds.put(st, nextStructType);
    fieldList.put(st, buildFieldList(st));
    nextStructType++;
  }

  public List<Pair<Integer, StructType>> getTypeList() {
    return Collections.unmodifiableList(structTypes);
  }

  /**
   * Get flattened list of fields and types, e.g.
   * 
   * [ ("a", int), ("b.field1", float), ("b.field2", float) ]
   * @param st
   * @return
   */
  public List<Pair<String, Type>> getFields(StructType st) {
    return fieldList.get(st);
  }
  
  private List<Pair<String, Type>> buildFieldList(StructType st) {
    List<Pair<String, Type>> fields = new ArrayList<Pair<String,Type>>();
    // Walk struct type including recursively visiting nested structs
    // and assemble a list of fields 
    addStructTypeFields(st, fields, "");
    return fields;
  }
  
  private static void addStructTypeFields(StructType st,
      List<Pair<String, Type>> fields, String prefix) {
    for (StructField f: st.getFields()) {
      if (Types.isStruct(f.getType())) {
        // Flatten out nested structs
        addStructTypeFields((StructType)f.getType(), fields,
                                prefix + f.getName() + ".");
      } else {
        // Otherwise add here
        fields.add(Pair.create(prefix + f.getName(), f.getType()));
      }
    }
  }

  public int getIDForType(StructType type) {
    Integer id = structTypeIds.get(type);
    assert(id != null): "Expected id to exist for type: " + type;
    return id;
  }

  /**
   * TODO: how to deal with nested structs????
   * @param structType
   * @param structField
   * @return
   */
  public int getFieldID(StructType structType, String structField) {
    List<Pair<String, Type>> fields = fieldList.get(structType);
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).val1.equals(structField)) {
        return i;
      }
    }
    throw new STCRuntimeError("Field not found in type: " + structField +
                              " " + structType); 
  }
}
