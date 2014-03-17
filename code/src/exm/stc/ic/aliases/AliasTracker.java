package exm.stc.ic.aliases;

import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.AliasCanonicalizer;

/**
 * Track which variables alias what.  AliasKey is a canonical key that
 * can be used to deal with aliases e.g. of struct fields.
 * 
 * This data structure lets us answer some basic questions:
 * - Does a variable exist in scope that corresponds to x.field1.field2?
 * - Is variable x a component of any struct?  
 */
public class AliasTracker implements AliasCanonicalizer {
  
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


  /**
   * Update alias info from instruction
   * @param inst
   * @return aliases from instruction for caller to use, if needed
   */
  public List<Alias> update(Instruction inst) {
    List<Alias> aliases = inst.getAliases(this);
    for (Alias alias: aliases) {
      addAlias(alias);
    }
    return aliases;
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
}