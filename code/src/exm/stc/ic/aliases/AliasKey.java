package exm.stc.ic.aliases;

import java.util.Arrays;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;

/**
 * A unique key used to identify a location, which may be a variable
 * or part of a variable.
 */
public class AliasKey implements Typed {
  public final Var var;
  public final String structPath[]; // Can be null for no path
  
  public AliasKey(Var var) {
    this(var, null);
  }
  
  public AliasKey splice(AliasKey subKey) {
    int splicedLen = structPath.length + subKey.structPath.length;
    String spliced[] = new String[splicedLen];
    
    for (int i = 0; i < structPath.length; i++) {
      spliced[i] = this.structPath[i];
    }
    for (int i = 0; i < subKey.structPath.length; i++) {
      spliced[i + structPath.length] = subKey.structPath[i];
    }
    return new AliasKey(this.var, spliced);
  }

  public AliasKey(Var var, String structPath[]) {
    assert(var != null);
    this.var = var;
    this.structPath = structPath;
  }
  
  public AliasKey makeChild(List<String> fieldPath,
                            boolean derefed) {
    int newPathLength = pathLength() + fieldPath.size();
    if (derefed) {
      newPathLength++; // For deref marker
    }
    
    String newPath[] = new String[newPathLength];
    for (int i = 0; i < pathLength(); i++) {
      newPath[i] = structPath[i];
    }
    
    for (int i = 0; i < fieldPath.size(); i++) {
      newPath[pathLength() + i] = fieldPath.get(i);
    }
    
    if (derefed) {
      newPath[newPathLength - 1] = AliasTracker.DEREF_MARKER;
    }
    
    return new AliasKey(var, newPath);
  }
  
  public int pathLength() {
    if (structPath == null) {
      return 0;
    }
    return structPath.length;
  }

  public Type type() {
    Type t = var.type();
    if (structPath != null) {
      for (String field: structPath) {
        if (field.equals(AliasTracker.DEREF_MARKER)) {
          t = Types.retrievedType(t);
        } else {
          assert(Types.isStruct(t) || Types.isStructLocal(t)) : t;
          t = ((StructType)t).getFieldTypeByName(field);
        }
      }
    }
    return t;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AliasKey)) {
      throw new STCRuntimeError("Comparing " + this.getClass() +
                                " with " + o.getClass());
    }
    AliasKey other = (AliasKey)o;
    if (!this.var.equals(other.var)) {
      return false;
    }
    if (this.pathLength() != other.pathLength()) {
      return false;
    }
    if (this.pathLength() == 0) {
      // Both have zero-length paths
      return true;
    }
    String p[] = this.structPath;
    String op[] = other.structPath;
    assert (p.length == op.length);
    for (int i = 0; i < p.length; i++) {
      if (!p[i].equals(op[i])) {
        return false;
      }
    }
    return true;  
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + var.hashCode();
    if (structPath != null) {
      for (int i = 0; i < structPath.length; i++) {
        result = prime * result + structPath [i].hashCode();
      }
    }
    return result;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(var.name());
    for (int i = 0; i < pathLength(); i++) {
      sb.append(".");
      sb.append(structPath[i]);
    }
    return sb.toString();
  }

  /**
   * Get the prefix of the current path
   * @param elems
   * @return
   */
  public AliasKey prefix(int elems) {
    assert(elems >= 0);
    if (elems > pathLength()) {
      throw new IllegalArgumentException("Too many elems: " + elems +
                                         " vs " + pathLength()); 
    }
    return new AliasKey(var, Arrays.copyOfRange(structPath, 0, elems));
  }
}