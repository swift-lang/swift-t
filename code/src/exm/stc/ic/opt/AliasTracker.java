package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Out;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.TurbineOp;

/**
 * Track which variables alias what.  AliasKey is a canonical key that
 * can be used to deal with aliases e.g. of struct fields.
 * 
 * This data structure lets us answer some basic questions:
 * - Does a variable exist in scope that corresponds to x.field1.field2?
 * - Is variable x a component of any struct?  
 */
public class AliasTracker {
  
  /**
   * String to add to path to indicate that it's a dereferenced value.
   * Note: we assume that this isn't a valid struct field name
   */
  public static final String DEREF_MARKER = "*";
  
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
      addAlias(alias);
    }
    return aliases;
  }

  private List<Alias> getInstructionAliases(Instruction inst) {
    switch (inst.op) {
      case STRUCT_CREATE_ALIAS:
        return buildStructAliases2(inst.getInput(0).getVar(),
                     ((TurbineOp)inst).getInputsTail(1),
                     inst.getOutput(0), false);
      case STRUCT_INIT_FIELDS: {
        Out<List<List<String>>> fieldPaths = new Out<List<List<String>>>();
        Out<List<Arg>> fieldVals = new Out<List<Arg>>();
        List<Alias> aliases = new ArrayList<Alias>();
        ((TurbineOp)inst).unpackStructInitArgs(fieldPaths, null, fieldVals);
        assert(fieldPaths.val.size() == fieldVals.val.size());
        
        for (int i = 0; i < fieldPaths.val.size(); i++) {
          List<String> fieldPath = fieldPaths.val.get(i);
          Arg fieldVal = fieldVals.val.get(i);
          if (fieldVal.isVar()) {
            aliases.addAll(buildStructAliases(inst.getOutput(0),
                                       fieldPath, fieldVal.getVar(), true));
          }
        }
        return aliases;
      }
      case STRUCT_RETRIEVE_SUB:
        return buildStructAliases2(inst.getInput(0).getVar(),
                     ((TurbineOp)inst).getInputsTail(1),
                     inst.getOutput(0), true);
      case STRUCT_STORE_SUB:
        return buildStructAliases2(inst.getOutput(0),
                     ((TurbineOp)inst).getInputsTail(1),
                     inst.getInput(0).getVar(), true);
      case STRUCT_COPY_OUT:
        return buildStructAliases2(inst.getInput(0).getVar(),
            ((TurbineOp)inst).getInputsTail(1),
            inst.getOutput(0), false);
      case STRUCT_COPY_IN:
        return buildStructAliases2(inst.getOutput(0),
            ((TurbineOp)inst).getInputsTail(1),
            inst.getInput(0).getVar(), false);
      case STORE_REF: {
        // need to track if ref is alias to struct field
        Var ref = inst.getOutput(0);
        AliasKey key = getCanonical(ref);
        if (key.pathLength() > 0) {
          assert(Types.isStruct(key.var));
          Var val = inst.getInput(0).getVar();
          return buildStructAliases(key.var, Arrays.asList(key.structPath),
                                    val, true);
        }
        break;
      }
      case LOAD_REF: {
        // need to track if ref is alias to struct field
        Var ref = inst.getInput(0).getVar();
        AliasKey key = getCanonical(ref);
        if (key.pathLength() > 0) {
          Var val = inst.getOutput(0);
          assert(Types.isStruct(key.var));
          return buildStructAliases(key.var, Arrays.asList(key.structPath),
                                    val, true);
        }
        break;
      }
      case COPY_REF: {
        // need to track if ref is alias to struct field
        Var ref1 = inst.getOutput(0);
        Var ref2 = inst.getInput(0).getVar();
        AliasKey key1 = getCanonical(ref1);
        AliasKey key2 = getCanonical(ref2);
        List<Alias> aliases = new ArrayList<Alias>();
        
        // Copy across alias info from one ref to another
        if (key1.pathLength() > 0) {
          assert(Types.isStruct(key1.var));
          aliases.addAll(
              buildStructAliases(key1.var, Arrays.asList(key1.structPath),
                                 ref2, false));
        }
        if (key2.pathLength() > 0) {
          assert(Types.isStruct(key2.var));
          aliases.addAll(
              buildStructAliases(key2.var, Arrays.asList(key2.structPath),
                                 ref1, false));
        }
        return aliases;
      }
      default:
        // Opcode not relevant
        break;
    }
    return Alias.NONE;
  }

  private List<Alias> buildStructAliases2(Var struct, List<Arg> fieldPath,
                                          Var val, boolean derefed) {
    return buildStructAliases(struct, Arg.extractStrings(fieldPath),
                              val, derefed);
  }

  /**
   * Helper to build appropriate alias for struct
   * @param struct
   * @param fieldPath
   * @param val
   * @param derefed if val is dereferenced type for struct field
   * @return
   */
  private List<Alias> buildStructAliases(Var struct, List<String> fieldPath,
                                         Var val, boolean derefed) {
    if (derefed) {
      // Value of field - only relevant if field is a reference
      if (fieldIsRef(struct, fieldPath)) {
        // val is the value of the reference
        return new Alias(struct, fieldPath, true, val).asList();
      } else {
        // Value of field - not an alias
        return Alias.NONE;
      }
      
    } else {
      // Straightforward alias of field
      return new Alias(struct, fieldPath, false, val).asList();
    }
  }

  /**
   * Return true if field of struct is a reference
   * @param struct
   * @param fieldPath
   * @return
   */
  private boolean fieldIsRef(Typed struct, List<String> fieldPath) {
    StructType type = (StructType)struct.type().getImplType();
    Type fieldType;
    try {
      fieldType = type.getFieldTypeByPath(fieldPath);
    } catch (TypeMismatchException e) {
      // Should only happen if we allowed bad IR to get generated
      throw new STCRuntimeError(e.getMessage());
    }
    assert(fieldType != null);
    return Types.isRef(fieldType);
  }

  public void addAlias(Alias alias) {
    addStructElem(alias.parent, alias.fieldPath, alias.derefed, alias.child);
  }

  public void addStructElem(Var parent, List<String> fieldPath, boolean derefed,
                            Var child) {
    
    // find canonical paths for parent and child
    AliasKey parentPath = getCanonical(parent);
    assert(parentPath != null);
    AliasKey childPath = parentPath.makeChild(fieldPath, derefed);
    
    assert(child.type().assignableTo(childPath.type())) 
            : child.type() + " vs " + childPath.type();
    
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
  public static class AliasKey implements Typed {
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
        newPath[newPathLength - 1] = DEREF_MARKER;
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
          if (field.equals(DEREF_MARKER)) {
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
  
  /**
   * Data class to store alias info
   */
  public static class Alias {

    public static final List<Alias> NONE = Collections.emptyList();
    
    public final Var parent;
    public final List<String> fieldPath;
    public final boolean derefed;
    public final Var child;
    

    public Alias(Var parent, List<String> fieldPath,
                  boolean derefed, Var child) {
      super();
      this.parent = parent;
      this.fieldPath = fieldPath;
      this.derefed = derefed;
      this.child = child;
    }


    public List<Alias> asList() {
      return Collections.singletonList(this);
    }
  }
}