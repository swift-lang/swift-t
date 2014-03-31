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
  public final String path[]; // Can be null for no path
  public final boolean hasUnknown;
  
  
  public AliasKey(Var var) {
    this(var, null);
  }
  
  public AliasKey(Var var, String path[]) {
    assert(var != null);
    this.var = var;
    this.path = path;
    this.hasUnknown = calcHasUnknown(path);
  }

  public AliasKey splice(AliasKey subKey) {
    if (subKey.pathLength() == 0) {
      return this;
    }
    
    int splicedLen = path.length + subKey.path.length;
    String spliced[] = new String[splicedLen];
    
    for (int i = 0; i < path.length; i++) {
      spliced[i] = this.path[i];
    }
    for (int i = 0; i < subKey.path.length; i++) {
      spliced[i + path.length] = subKey.path[i];
    }
    return new AliasKey(this.var, spliced);
  }

  public static AliasKey create(Var var, List<String> pathList) {
    return new AliasKey(var, pathListToArray(pathList));
  }

  private static String[] pathListToArray(List<String> pathList) {
    String path[] = new String[pathList.size()];
    int i = 0;
    for (String s: pathList) {
      path[i++] = s;
    }
    return path;
  }
  
  public AliasKey makeChild(List<String> fieldPath,
                            boolean derefed) {
    int newPathLength = pathLength() + fieldPath.size();
    if (derefed) {
      newPathLength++; // For deref marker
    }
    
    String newPath[] = new String[newPathLength];
    for (int i = 0; i < pathLength(); i++) {
      newPath[i] = path[i];
    }
    
    for (int i = 0; i < fieldPath.size(); i++) {
      newPath[pathLength() + i] = fieldPath.get(i);
    }
    
    if (derefed) {
      newPath[newPathLength - 1] = Alias.DEREF_MARKER;
    }
    
    return new AliasKey(var, newPath);
  }
  
  public int pathLength() {
    if (path == null) {
      return 0;
    }
    return path.length;
  }

  /**
   * @return true if it's a simple alias of a struct field
   */
  public boolean isPlainStructAlias() {
    if (pathLength() == 0) {
      return false;
    }

    Type t = var.type();
    if (!Types.isStruct(t)) {
      return false;
    }
    if (path != null) {
      for (String field: path) {
        if (field.equals(Alias.DEREF_MARKER)) {
          return false;
        } else {
          assert(Types.isStruct(t) || Types.isStructLocal(t)) : t;
          t = ((StructType)t).getFieldTypeByName(field);
        }
      }
    }
    return true;
  }

  public boolean isFilenameAlias() {
    return Types.isFile(var) && pathLength() == 1 &&
        path[0] == Alias.FILENAME;
  }
  

  public boolean isArrayMemberAlias() {
    return Types.isArray(var) && pathLength() == 1 &&
        path[0] == Alias.ARRAY_SUBSCRIPT;
  }

  public Type type() {
    Type t = var.type();
    if (path != null) {
      for (String field: path) {
        if (field == Alias.UNKNOWN) {
          assert(Types.isContainer(t)) : t;
          t = Types.containerElemType(t);
        } else if (field.equals(Alias.DEREF_MARKER)) {
          t = Types.retrievedType(t);
        } else if (Types.isFile(t) && field.equals(Alias.FILENAME)) {
          t = Types.F_STRING;
        } else {
          assert(Types.isStruct(t) || Types.isStructLocal(t)) : t;
          t = ((StructType)t).getFieldTypeByName(field);
        }
      }
    }
    return t;
  }

  /**
   * Any alias key with UNKNOWN is non equal to anything else
   * @param o
   * @return
   */
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
    String p[] = this.path;
    String op[] = other.path;
    assert (p.length == op.length);
    for (int i = 0; i < p.length; i++) {
      if (p[i] == Alias.UNKNOWN || op[i] == Alias.UNKNOWN) {
        // Don't treat unknowns as equal
        return false;
      }
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
    if (path != null) {
      for (int i = 0; i < path.length; i++) {
        int fieldHashCode;
        if (path[i] == null) {
          fieldHashCode = 0;
        } else {
          fieldHashCode = path[i].hashCode();
        }
        result = prime * result + fieldHashCode;
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
      sb.append(path[i]);
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
    return new AliasKey(var, Arrays.copyOfRange(path, 0, elems));
  }

  /**
   * @return true if there's an unknown in the path
   */
  public boolean hasUnknown() {
    return hasUnknown;
  }
  
  private static boolean calcHasUnknown(String path[]) {
    if (path == null) {
      return false;
    }
    for (int i = 0; i < path.length; i++) {
      if (path[i] == Alias.UNKNOWN) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if theres a deref in the path;
   */
  public boolean hasDeref() {
    return hasDeref(0);
  }
  
  /**
   * @return true if there's a deref in the path from startIX onwards
   */
  public boolean hasDeref(int startIx) {
    for (int i = 0; i < pathLength(); i++) {
      if (path[i] != Alias.UNKNOWN &&
          path[i].equals(Alias.DEREF_MARKER)) {
        return true;
      }
    }
    return false;
  }
}