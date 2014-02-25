package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.Opcode;

/**
 * Track which variables alias what.  AliasKey is a canonical key that
 * can be used to deal with aliases e.g. of struct fields.
 * 
 * This data structure lets us answer some basic questions:
 * - Does a variable exist in scope that corresponds to x.field1.field2?
 * - Is variable x a component of any struct?  
 */
public class AliasTracker {
  private final Logger logger = Logging.getSTCLogger();
  
  private final AliasTracker parent;
  
  /**
   * Map from variable to alias key.
   * 
   * Note that it is possible that a variable can be a part of more than
   * one struct.  In this case we use the first definition encountered in
   * code and don't track subsequent ones.
   */
  private final HierarchicalMap<Var, AliasKey> varToPath;
  
  /**
   * Map from alias key to element vars.  We only track the first variable
   * encountered for each alias.
   */
  private final HierarchicalMap<AliasKey, Var> pathToVar;
  
  
  /**
   * All keys that the var is the root of
   */
  private final MultiMap<Var, Pair<Var, AliasKey>> roots;

  public AliasTracker() {
    this(null, new HierarchicalMap<Var, AliasKey>(),
        new HierarchicalMap<AliasKey, Var>());
  }
  
  private AliasTracker(AliasTracker parent,
                       HierarchicalMap<Var, AliasKey> varToPath,
                       HierarchicalMap<AliasKey, Var> pathToVar) {
    this.parent = parent;
    this.varToPath = varToPath;
    this.pathToVar = pathToVar;
    // We need to look at parent to find additional roots
    this.roots = new MultiMap<Var, Pair<Var, AliasKey>>();
  }
  
  public AliasTracker makeChild() {
    return new AliasTracker(this, varToPath.makeChildMap(), 
                            pathToVar.makeChildMap());
  }


  public List<Alias> update(Instruction inst) {
    List<Alias> aliases = getInstructionAliases(inst);
    for (Alias alias: aliases) {
      addStructElem(alias.parent, alias.field, alias.child);
    }
    return aliases;
  }

  public List<Alias> getInstructionAliases(Instruction inst) {
    if (inst.op == Opcode.STRUCT_CREATE_ALIAS) {
      return Collections.singletonList(new Alias(inst.getInput(0).getVar(),
                            inst.getInput(1).getStringLit(),
                            inst.getOutput(0)));
    }
    return Collections.emptyList();
  }

  public void addStructElem(Var parent, String field, Var child) {
    
    // find canonical paths for parent and child
    AliasKey parentPath = getCanonical(parent);
    assert(parentPath != null);
    AliasKey childPath = parentPath.makeChild(field);
    
    addVarPath(child, childPath, null);
  }

  /**
   * Add a var and corresponding path, update data structures to be consistent
   * @param var
   * @param path path, which should have at least one element
   * @param replacePath if a root was updated, and we should override the
   *              previous entry in varToPath in event of a conflict
   */
  public void addVarPath(Var var, AliasKey path, AliasKey replacePath) {
    assert(!path.var.equals(var));
    assert(path.pathLength() >= 1);
    
    roots.put(path.var, Pair.create(var, path));
    
    Var prevChild = pathToVar.get(path); 
    if (prevChild == null || (prevChild.storage() == Alloc.ALIAS &&
                                 var.storage() != Alloc.ALIAS)) {
      // Prefer non-alias vars
      pathToVar.put(path, var);
    }
    
    AliasKey prevPath = varToPath.get(var);
    // First entry takes priority, unless we're explicitly replacing an old path
    if (prevPath == null || (replacePath != null && prevPath.equals(replacePath))) {
      varToPath.put(var, path);
    } else {
      // It is ok if a var is a part of multiple structs
      logger.trace(prevPath + " and " + path + 
                   " both lead to " + var);
    }
    
    updateRoot(var, path);
  }

  /**
   * Check to see if var is the root of any keys, and if so
   * update them to reflect that they're part of a larger structure 
   * @param childPath
   * @param child
   */
  private void updateRoot(Var var, AliasKey newPath) {
    assert(newPath.pathLength() >= 1);
    
    // Traverse parents
    AliasTracker curr = this;
    while (curr != null) {
      for (Pair<Var, AliasKey> varKey: curr.roots.get(var)) {
        Var desc = varKey.val1;
        AliasKey descKey = varKey.val2;
        assert(descKey.var.equals(var));
        AliasKey newKey = newPath.splice(descKey);
        // This should only be effective at this level and below in hierarchy
        addVarPath(desc, newKey, descKey);
      }
      curr = curr.parent;
    }
  }

  public Var findVar(Var var, String field) {
    // Construct a canonical key for the child
    AliasKey varKey = getCanonical(var);
    assert(varKey != null);
    AliasKey childKey = varKey.makeChild(field);
    return pathToVar.get(childKey);
  }

  /**
   * See if a variable exists corresponding to key
   * @param key
   * @return null if no variable yet, the variable if present
   */
  public Var findVar(AliasKey key) {
    assert(key != null);
    if (key.pathLength() == 0) {
      return key.var;
    } else {
      return pathToVar.get(key);
    }
  }


  /**
   * Find the canonical root and path corresponding to the variable
   * @param var
   * @return the key containing root variable, and path from root.
   *        Should not be null
   */
  public AliasKey getCanonical(Var var) {
    AliasKey key = varToPath.get(var);
    if (key != null) {
      return key;
    } else {
      // Not a part of any structure
      return new AliasKey(var);
    }
  }
  
  @Override
  public String toString() {
    return "varToPath: " + varToPath +
            "\n    pathToVar: " + pathToVar;
  }
  
  /**
   * Something for which refcount is tracked in this module
   */
  public static class AliasKey {
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
    
    public AliasKey makeChild(String field) {
      String newPath[] = new String[pathLength() + 1];
      for (int i = 0; i < pathLength(); i++) {
        newPath[i] = structPath[i];
      }
      newPath[pathLength()] = field;
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
          assert(Types.isStruct(t) || Types.isStructLocal(t)) : t;
          t = ((StructType)t).getFieldTypeByName(field);
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
  
  /**
   * Data class to store alias info
   */
  public static class Alias {
    public final Var parent;
    public final String field;
    public final Var child;
    

    public Alias(Var parent, String field, Var child) {
      super();
      this.parent = parent;
      this.field = field;
      this.child = child;
    }
  }
}