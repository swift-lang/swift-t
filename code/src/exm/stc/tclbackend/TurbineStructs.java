package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.StructType;
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
    List<StructField> fields = structType.getFields();
    for (int i = 0; i < fields.size(); i++) {
      StructField f = fields.get(i);
      if (structField.equals(f.getName()))
        return i;
    }
    throw new STCRuntimeError("Field not found in type: " + structField +
                              " " + structType); 
  }
}
