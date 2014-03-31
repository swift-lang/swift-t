package exm.stc.ic.aliases;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
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
   * Map from variable to all alias keys added in this scope.
   * 
   * Note that it is possible that a variable can be a part of more than
   * one thing.
   * 
   * The first element of the list (if present) will be the canonical one.
   * Aliases may also exist in ancestor scopes. 
   */
  private final MultiMap<Var, AliasKey> varToPath;
  

  /**
   * Map from variable to alias key that is copy of alias key, but could
   * be dereferenced to yield valid alias.  I.e. var should be a ref variable.
   */
  private final MultiMap<Var, AliasKey> refCopyVarToPath;
  
  /**
   * Map from alias key to element vars
   */
  private final MultiMap<AliasKey, Var> pathToVar;
  
  
  /**
   * All keys that the var is the root of
   */
  private final MultiMap<Var, Pair<Var, AliasKey>> roots;

  public AliasTracker() {
    this(null);
  }
  
  private AliasTracker(AliasTracker parent) {
    this.parent = parent;
    this.varToPath = new MultiMap<Var, AliasKey>();
    this.pathToVar = new MultiMap<AliasKey, Var>();
    this.refCopyVarToPath = new MultiMap<Var, AliasKey>();
    // We need to look at parent to find additional roots
    this.roots = new MultiMap<Var, Pair<Var, AliasKey>>();
  }
  
  public AliasTracker makeChild() {
    return new AliasTracker(this);
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
    if (logger.isTraceEnabled()) {
      logger.trace("addAlias: " + parent + "." + fieldPath
                + " == " + child + " (" + transform + ")");
    }
    
    boolean derefed = (transform == AliasTransform.RETRIEVE);
    
    // find paths for parent and child
    // If we're going to deref, doesn't matter if it's a copy
    Set<AliasKey> parentPaths = getAllKeys(parent, true, derefed);
    
    // If we didn't have a precise path, want to add one
    boolean foundPrecisePath = false;
    for (AliasKey parentPath: parentPaths) {
      AliasKey childPath = parentPath.makeChild(fieldPath, derefed);

      if (logger.isTraceEnabled()) {
        logger.trace("Found parentPath: " + parentPath + " for " + parent 
                      + " => " + childPath);
      }
      assert(child.type().assignableTo(childPath.type())) 
              : child.type() + " vs " + childPath.type();
      
      if (parentPath.pathLength() > 0 || !parentPath.var.equals(child)) {
        addNewVarPath(child, childPath, transform);
        foundPrecisePath = foundPrecisePath || !parentPath.hasUnknown();
      }
    }
    
    if (!foundPrecisePath) {
      // Ensure at least one precise path added
      addNewVarPath(child, new AliasKey(parent).makeChild(fieldPath, derefed),
                    transform);
    }
  }

  private void addNewVarPath(Var var, AliasKey path,
      AliasTransform transform) {
    if (transform == AliasTransform.IDENTITY ||
        transform == AliasTransform.RETRIEVE) {
      addVarPath(var, path, null);
    } else {
      addRefCopyVarPath(var, path);
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
    roots.put(path.var, Pair.create(var, path));

    AliasKey canonPath = updateVarToPath(var, path, replacePath);
    if (logger.isTraceEnabled()) {
      logger.trace("Add varToPath: " + var + " => " + path + " canon "
                  + canonPath);
    }
    updatePathToVar(var, path);
    if (path != canonPath) {
      updatePathToVar(var, canonPath);
    }
    
    updateRoot(var, path);
  }

  /**
   * Update varToPath map
   * @param var
   * @param path
   * @param replacePath
   * @return canonical path
   */
  private AliasKey
      updateVarToPath(Var var, AliasKey path, AliasKey replacePath) {
    /*
     * Ensure new ones added, and ensure that canonicals are first in list
     */
    AliasKey canonPath = path;
    AliasTracker curr = this;
    boolean foundPrevPath = false;
    while (curr != null && !foundPrevPath) {
      for (AliasKey prevPath: varToPath.get(var)) {
        // Can assume that first thing we encounter is previous canonical
        // First entry without unknowns takes priority
        if (!prevPath.hasUnknown() || path.hasUnknown() &&
            !(replacePath != null && canonPath.equals(replacePath))) {
          canonPath = prevPath;
        } else {
          // It is ok if a var is a part of multiple structs
          logger.trace(prevPath + " and " + path + 
                       " both lead to " + var);
        }
        foundPrevPath = true;
        break;
      }
      curr = curr.parent;
    }
    
    // Apply the changes to var => path mapping to the current scope
    if (path == canonPath) {
      setAsCanonicalPath(var, path);
    } else {
      // Add new one first
      varToPath.put(var, path);
      // Ensure canonical is still canonical
      setAsCanonicalPath(var, canonPath);
    }
    return canonPath;
  }

  /**
   * Update pathToVar map
   * @param var 
   * @param path
   * @return canonical var
   */
  private Var updatePathToVar(Var var, AliasKey path) {
    if (path.hasUnknown()) {
      // Don't compare paths with unknown components
      return var;
    }
    
    AliasTracker curr = this;
    // Search for other vars associated with path
    boolean foundPrevVar = false;
    Var canonVar = var;
    while (curr != null && !foundPrevVar) {
      for (Var prevVar: pathToVar.get(path)) {
        // Prefer non-alias, or first encountered
        if (var.storage() == Alloc.ALIAS || prevVar.storage() != Alloc.ALIAS) {
          canonVar = prevVar;
        }
        
        if (logger.isTraceEnabled()) {
          logger.trace("Found prev var for path " + path + " " +
                       prevVar + " vs " + var); 
        }
        foundPrevVar = true;
        break;
      }
      curr = curr.parent;
    }
    
    
    if (var == canonVar) {
      setAsCanonicalVar(var, path);
    } else {
      pathToVar.put(path, var);
      setAsCanonicalVar(canonVar, path);
    }
    
    return canonVar;
  }

  private void setAsCanonicalVar(Var var, AliasKey path) {
    List<Var> currList = pathToVar.get(path);
    if (currList.isEmpty()) {
      // Just add
      pathToVar.put(path, var);
    } else {
      // Ensure new canonical is first in list
      if (!currList.get(0).equals(var)) {
        currList.add(0, var);
      }
    }
  }

  private void setAsCanonicalPath(Var var, AliasKey path) {
    List<AliasKey> currList = varToPath.get(var);
    if (currList.isEmpty()) {
      // Just add
      varToPath.put(var, path);
    } else {
      // Ensure new canonical is first in list
      if (!currList.get(0).equals(path)) {
        currList.add(0, path);
      }
    }
  }

  /**
   * Check to see if var is the root of any keys, and if so
   * update them to reflect that they're part of a larger structure 
   * @param childPath
   * @param child
   */
  private void updateRoot(Var var, AliasKey newPath) {
    if (newPath.pathLength() == 0) {
      return;
    }
    
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
      // Find the one in the innermost scope
      AliasTracker curr = this;
      while (curr != null) {
        List<Var> vars = curr.pathToVar.get(key);
        if (vars.size() > 0) {
          // Return first one
          return vars.get(0);
        }
        curr = curr.parent;
      }
      return null;
    }
  }


  private Set<AliasKey> getAllKeys(Var var, boolean includeUnknown,
                                  boolean includeCopies) {
    Set<AliasKey> result = new HashSet<AliasKey>();
    
    AliasTracker curr = this;
    while (curr != null) {
      for (AliasKey key: varToPath.get(var)) {
        if (includeUnknown || !key.hasUnknown()) {
          result.add(key);
        }
      }
      if (includeCopies) {
        result.addAll(refCopyVarToPath.get(var));
      }
      curr = curr.parent;
    }
    
    return result;
  }

  /**
   * Find the canonical root and path corresponding to the variable
   * @param var
   * @return the key containing root variable, and path from root.
   *        Should not be null and should not contain unknown
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
    AliasTracker curr = this;
    while (curr != null) {
      List<AliasKey> keys = curr.varToPath.get(var);
      if (keys.size() > 0) {
        AliasKey best = keys.get(0);
        if (best.hasUnknown()) {
          // There will be no keys here with unknown components
          break;
        } else {
          return best;
        }
      } 
      curr = curr.parent;
    }
    
    if (includeCopies) {
      while (curr != null) {
        List<AliasKey> keys = curr.refCopyVarToPath.get(var);
        if (keys.size() > 0) {
          AliasKey best = keys.get(0);
          if (best.hasUnknown()) {
            // There will be no keys with unknown components
            break;
          } else {
            return best;
          }
        } 
        curr = curr.parent;
      }
    }
    
    // Not a part of any structure
    return new AliasKey(var);
  }
  

  /**
   * Get the root of the datum containing the variable
   * @param var
   * @return
   */
  public Var getDatumRoot(Var var) {
    Var best = var;
    int bestSteps = 0; // Number of steps up from var
    
    // We're looking for the longest key without a deref
    AliasTracker curr = this;
    while (curr != null) {
      for (AliasKey key: curr.varToPath.get(var)) {
        AliasKey rootKey = getDatumRootKey(key);
        int steps = key.pathLength() - rootKey.pathLength();
        assert(steps >= 0);
        if (steps > bestSteps) {
          Var rootVar = findVar(rootKey);
          if (rootVar != null) {
            bestSteps = steps;
            best = rootVar;
          }
        }
      }
      curr = curr.parent;
    }
    return best;
  }
  
  /**
   * Get the root of the datum represented by the key.
   * Often this is just the root of the key, but if there is a reference
   * involved, this gets the last referand, rather than referee
   * @param key
   * @return
   */
  public static AliasKey getDatumRootKey(AliasKey key) {
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
        return prefixKey;
      }
    }
    
    // If no references, struct root
    return new AliasKey(key.var);
  }
  
  /**
   * Find variables that are a part of this var
   * @param var
   * @param followRefs
   * @return
   */
  public List<Var> getDatumComponents(Var var, boolean followRefs) {
    List<Var> results = new ArrayList<Var>();
    // Find root of this var to narrow down search
    Set<AliasKey> keys = getAllKeys(var, false, false);
    AliasTracker curr = this;
    while (curr != null) {
      for (AliasKey key: keys) {
        getDatumComponents(curr, var, followRefs, results, key);
      }
      curr = curr.parent;
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
      
      if (followRefs ||
          !componentKey.hasDeref(canon.pathLength())) {
        // Skip checking references
        results.add(componentVar);
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
    for (AliasKey key: getAllKeys(var, true, false)) {
      logger.trace("getAncestors() key=" + key);
      addAncestors(results, key);
    }      
    return results;
  }

  private void addAncestors(List<Pair<AliasKey, Boolean>> results,
                            AliasKey key) {
    boolean traversedDeref = false;
    for (int i = key.pathLength() - 1; i >= 0; i--) {
      if (key.path[i] != null &&
          key.path[i].equals(Alias.DEREF_MARKER)) {
        // Traversed ref
        traversedDeref = true;
      }
      AliasKey prefix = key.prefix(i);
      if (logger.isTraceEnabled()) {
        logger.trace("ancestor: (" + prefix + ", " + traversedDeref + ")");
      }      
      results.add(Pair.create(prefix, traversedDeref));
    }
  }

  @Override
  public String toString() {
    return "varToPath: " + varToPath +
            "\n    pathToVar: " + pathToVar;
  }
}