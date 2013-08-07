package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;

/**
 * Track which variables alias what.  AliasKey is a canonical key that
 * can be used to deal with aliases e.g. of struct fields
 */
public class AliasTracker {
  private final Logger logger = Logging.getSTCLogger();
  
  /**
   * Map from variables at bottom of struct to root.
   * 
   * Note that it is possible that a variable can be a part of
   * more than one struct.  In this case we use the first definition
   * encountered in code and don't track subsequent ones.
   */
  private final HierarchicalMap<Var, Pair<Var, String>> elemOfStruct;
  
  /**
   * Map from root of struct to element vars
   */
  private HierarchicalMap<Pair<Var, String>, Var> structElem;


  public AliasTracker() {
    this(new HierarchicalMap<Var, Pair<Var,String>>(),
        new HierarchicalMap<Pair<Var,String>, Var>());
  }
  
  private AliasTracker(HierarchicalMap<Var, Pair<Var, String>> elemOfStruct,
      HierarchicalMap<Pair<Var, String>, Var> structElem) {
    this.elemOfStruct = elemOfStruct;
    this.structElem = structElem;
  }
  
  public AliasTracker makeChild() {
    return new AliasTracker(elemOfStruct.makeChildMap(), 
                            structElem.makeChildMap());
  }


  public List<Alias> update(Instruction inst) {
    List<Alias> aliases = getInstructionAliases(inst);
    for (Alias alias: aliases) {
      addStructElem(alias.parent, alias.field, alias.child);
    }
    return aliases;
  }

  public List<Alias> getInstructionAliases(Instruction inst) {
    if (inst.op == Opcode.STRUCT_INSERT) {
      return Collections.singletonList(new Alias(inst.getOutput(0), 
                            inst.getInput(0).getStringLit(),
                            inst.getInput(1).getVar()));
    } else if (inst.op == Opcode.STRUCT_LOOKUP) {
      return Collections.singletonList(new Alias(inst.getInput(0).getVar(),
                            inst.getInput(1).getStringLit(),
                            inst.getOutput(0)));
    }
    return Collections.emptyList();
  }

  public void addStructElem(Var parent, String field, Var child) {
    Pair<Var, String> parentField = Pair.create(parent, field);
    Var prevChild = structElem.get(parentField); 
    if (prevChild == null || (prevChild.storage() == VarStorage.ALIAS &&
                                 child.storage() != VarStorage.ALIAS)) {
      // Prefer non-alias vars
      structElem.put(parentField,  child);
    }
    
    Pair<Var, String> prevParentField = elemOfStruct.get(child);
    if (prevParentField == null) {
      elemOfStruct.put(child, parentField);
    } else {
      // It is ok if a var is a part of multiple structs
      logger.trace(prevParentField + " and " + parentField + 
                   " are aliases for " + child);
    }
  }
  

  public Pair<Var, String> getStructParent(Var curr) {
    return elemOfStruct.get(curr);
  }


  public Var findVar(Var var, String fieldName) {
    return structElem.get(Pair.create(var, fieldName));
  }

  /**
   * See if a variable exists corresponding to key
   * @param key
   * @return null if no variable yet, the variable if present
   */
  public Var findVar(AliasKey key) {
    if (key.pathLength() == 0) {
      return key.var;
    }
    
    Var curr = key.var;
    for (String field: key.structPath) {
      curr = structElem.get(Pair.create(curr, field));
      if (curr == null) {
        // Doesn't exist
        break;
      }
    }
    return curr;
  }

  /**
   * Find the root corresponding to the variable
   * @param var
   * @return the root variable, and path from var to root
   */
  public Pair<Var, List<String>> findRoot(Var var) {
    Var curr = var;
    List<String> path = new ArrayList<String>();
    while (elemOfStruct.containsKey(curr)) {
      Pair<Var, String> par = elemOfStruct.get(curr);
      path.add(par.val2);
      curr = par.val1;
    }
    
    return Pair.create(curr, path);
  }


  public AliasKey getCanonical(Var var) {
    Pair<Var, List<String>> r = findRoot(var);
    Var root = r.val1;
    List<String> path = r.val2;
    if (path.isEmpty()) {
      assert(var == root);
      return new AliasKey(var);
    } else {
      // Reverse the path
      String arr[] = new String[path.size()];
      for (int i = 0; i < path.size(); i++) {
        arr[i] = path.get(path.size() - 1 - i);
      }
      // Get root struct var and path to refcounted var
      return new AliasKey(root, arr);
    }
  }
  
  @Override
  public String toString() {
    return "ElemOfStruct: " + elemOfStruct +
            "\n    StructElem: " + structElem;
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
          assert(Types.isStruct(t)) : t;
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