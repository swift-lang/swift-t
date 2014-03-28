package exm.stc.ic.aliases;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.aliases.Alias.AliasTransform;
import exm.stc.ic.tree.ICInstructions.Instruction;

/**
 * Track which variables alias what.  AliasKey is a canonical key that
 * can be used to deal with aliases e.g. of struct fields.
 * 
 * This data structure lets us answer some basic questions:
 * - Does a variable exist in scope that corresponds to x.field1.field2?
 * - Is variable x a component of any struct?
 * - Is variable x a component of any other structure
 * 
 * We use a definition of canonical path, where there are multiple possible
 * paths to a variable.  A path is canonical if the root of the path is not
 * a child of any other variable with a known path.  This favours precise
 * paths, which are more useful.
 */
public class AliasTracker {
  
  private final Logger logger = Logging.getSTCLogger();
  
  private final AliasTracker parent;
  
  /**
   * Map from variable to alias key.
   * 
   * Note that it is possible that a variable can be a part of more than
   * one thing.  In this case we use the first definition encountered in
   * code and don't track subsequent ones.
   */
  private final HierarchicalMap<Var, AliasKey> varToPath;
  

  /**
   * Map from variable to alias key that is copy of alias key, but could
   * be dereferenced to yield valid alias.  I.e. var should be a ref variable.
   */
  private final HierarchicalMap<Var, AliasKey> refCopyVarToPath;
  
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
        new HierarchicalMap<Var, AliasKey>(),
        new HierarchicalMap<AliasKey, Var>());
  }
  
  private AliasTracker(AliasTracker parent,
                       HierarchicalMap<Var, AliasKey> varToPath,
                       HierarchicalMap<Var, AliasKey> refCopyVarToPath,
                       HierarchicalMap<AliasKey, Var> pathToVar) {
    this.parent = parent;
    this.varToPath = varToPath;
    this.pathToVar = pathToVar;
    this.refCopyVarToPath = refCopyVarToPath;
    // We need to look at parent to find additional roots
    this.roots = new MultiMap<Var, Pair<Var, AliasKey>>();
  }
  
  public AliasTracker makeChild() {
    return new AliasTracker(this, varToPath.makeChildMap(),
        refCopyVarToPath.makeChildMap(), pathToVar.makeChildMap());
  }


  /**
   * Update alias info from instruction
   * @param inst
   * @return aliases from instruction for caller to use, if needed
   */
  public List<Alias> update(Instruction inst) {
    List<Alias> aliases = inst.getAliases();
    update(aliases);
    return aliases;
  }

  public void update(List<Alias> aliases) {
    for (Alias alias: aliases) {
      addAlias(alias);
    }
  }

  public void addAlias(Alias alias) {
    addAlias(alias.parent, alias.fieldPath, alias.transform, alias.child);
  }

  public void addAlias(Var parent, List<String> fieldPath,
      AliasTransform transform, Var child) {
    boolean derefed = (transform == AliasTransform.RETRIEVE);
    
    // find canonical paths for parent and child
    // If we're going to deref, doesn't matter if it's a copy
    AliasKey parentPath = getCanonical(parent, derefed);
    assert(parentPath != null);
    

    // Need to check if it's a precise path: don't want to reduce precision
    if (parentPath.hasUnknown()) {
      parentPath = new AliasKey(parent);
    }
   
    AliasKey childPath = parentPath.makeChild(fieldPath, derefed);
    assert(child.type().assignableTo(childPath.type())) 
            : child.type() + " vs " + childPath.type();
    
    if (transform == AliasTransform.IDENTITY ||
        transform == AliasTransform.RETRIEVE) {
      addVarPath(child, childPath, null);
    } else {
      addRefCopyVarPath(child, childPath);
    }
  }

  /**
   * Add a var and corresponding path, update data structures to be consistent
   * @param var
   * @param path path, which should have at least one element
   * @param replacePath if a root was updated, and we should override the
   *              previous entry in varToPath in event of a conflict
   */
  private void addVarPath(Var var, AliasKey path, AliasKey replacePath) {
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
    // First entry without unknowns takes priority, unless we're explicitly 
    // replacing an old path
    if (prevPath == null || 
        (replacePath != null && prevPath.equals(replacePath)) ||
        (prevPath.hasUnknown() && !path.hasUnknown())) {
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
    
    // Don't update unless precise
    if (newPath.hasUnknown()) {
      return;
    }
    
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

  private void addRefCopyVarPath(Var var, AliasKey path) {
    refCopyVarToPath.put(var, path);
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
    return getCanonical(var, false);
  }

  /**
   * Find the canonical root and path corresponding to the variable
   * @param var
   * @param includeCopies if we can't find a true alias, is a copy ok?
   * @return the key containing root variable, and path from root.
   *        Should not be null
   */
  private AliasKey getCanonical(Var var, boolean includeCopies) {
    AliasKey key = varToPath.get(var);
    if (key != null) {
      return key;
    } else if (includeCopies) {
      key = refCopyVarToPath.get(var);
      if (key != null) {
        return key;
      }
    }
    
    // Not a part of any structure
    return new AliasKey(var);
  }
  
  /**
   * Get the root of the datum represented by the key.
   * Often this is just the root of the key, but if there is a reference
   * involved, this gets the last referand, rather than referee
   * @param key
   * @return
   */
  public Var getDatumRoot(AliasKey key) {
    // If inside struct, check to see if there is a reference
    for (int i = key.pathLength() - 1; i >= 0; i--) {
      if (key.path[i] != Alias.UNKNOWN &&
          key.path[i].equals(Alias.DEREF_MARKER)) {
        // Refcount var key includes prefix including deref marker:
        // I.e. it should be the ID of the datum being refcounting
        String pathPrefix[] = new String[i + 1];
        for (int j = 0; j <= i; j++) {
          pathPrefix[j] = key.path[j];
        }
        AliasKey prefixKey = new AliasKey(key.var, pathPrefix);
        Var refcountVar = findVar(prefixKey);
        assert(refcountVar != null) : "Expected var for alias key " + key +
                                       " to exist";
        return refcountVar;
      }
    }
    
    // If no references, struct root
    return key.var;
  }
  
  public List<Var> getDatumComponents(Var var, boolean followRefs) {
    List<Var> results = new ArrayList<Var>();
    // Find root of this var to narrow down search
    AliasKey currKey = getCanonical(var);
    
    while (currKey != null) {
      AliasTracker curr = this;
      while (curr != null) {
        getDatumComponents(curr, var, followRefs, results, currKey); 
        curr = curr.parent;
      }
    
      // there might be an ancestor key of canon if there was an unknown in
      // the path somewhere
      AliasKey next = getCanonical(currKey.var);
      if (next.pathLength() > 0) {
        currKey = next.splice(currKey);
      } else {
        // Done
        currKey = null;
      }
    }
    return results;
  }

  private static void getDatumComponents(AliasTracker tracker, Var var,
      boolean followRefs, List<Var> results, AliasKey canon) {
    for (Pair<Var, AliasKey> component: tracker.roots.get(canon.var)) {
      Var componentVar = component.val1;
      AliasKey componentKey = component.val2;
      if (componentVar.equals(var)) {
        continue;
      }
      if (!prefixMatch(canon, componentKey)) {
        continue;
      }
      
      if (followRefs) {
        // Skip checking references
        results.add(componentVar);
      } else {
        // Need to make sure it is part of same datum, i.e. doesn't
        // follow a ref
        boolean followsRef = false;
        for (int i = canon.pathLength(); i < componentKey.pathLength(); i++) {
          if (componentKey.path[i].equals(Alias.DEREF_MARKER)) {
            followsRef = true;
            break;
          }
        }
        if (!followsRef) {
          results.add(componentVar);
        }
      }
    }
  }

  /**
   * Check if key1's path is a prefix of key2's path
   * @param key1
   * @param key2
   * @param prefixLne
   * @return
   */
  private static boolean prefixMatch(AliasKey key1, AliasKey key2) {
    // Check that prefixes match

    if (key2.pathLength() < key1.pathLength()) {
      // Cannot be a component
      return false;
    }
    for (int i = 0; i < key1.pathLength(); i++) {
      String elem1 = key1.path[i];
      String elem2 = key2.path[i];
      if (elem1 != elem2 &&
          (elem1 == null || elem2 == null || !elem1.equals(elem2))) {
            // prefix doesn't match
        return false;
      }
    }
    return true;
  }

  /**
   * Return list of ancestor datums
   * @param var
   * @return list of (ancestorKey, if part of separate structure) 
   */
  public List<Pair<AliasKey, Boolean>> getAncestors(Var var) {
    if (logger.isTraceEnabled()) {
      logger.trace("getAncestors(" + var + ")");
    }
    
    List<Pair<AliasKey, Boolean>> results = 
        new ArrayList<Pair<AliasKey, Boolean>>();
    // Try tracing back through parents
    AliasKey canonKey = getCanonical(var);
    
    logger.trace("CanonKey: " + canonKey);
    
    boolean traversedDeref = false;
    for (int i = canonKey.pathLength() - 1; i >= 0; i--) {
      AliasKey prefix = canonKey.prefix(i);
      if (logger.isTraceEnabled()) {
        logger.trace("ancestor: (" + prefix + ", " + traversedDeref + ")");
      }      
      results.add(Pair.create(prefix, traversedDeref));
      if (canonKey.path[i] != null &&
          canonKey.path[i].equals(Alias.DEREF_MARKER)) {
        // Traversed ref
        traversedDeref = true;
      }
    }
    
    return results;
  }

  @Override
  public String toString() {
    return "varToPath: " + varToPath +
            "\n    pathToVar: " + pathToVar;
  }
}