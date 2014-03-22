package exm.stc.ic.componentaliases;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;

/**
 * Graph that can track component relationships between different variables 
 * 
 * Features:
 * - Designed to track *any* potential component relationship:
 *   structs, arrays, etc
 * - No false negatives, only false positives (i.e. it determines whether
 *   there *might* be a relationship) 
 * - No attempt to implemented scoping
 * - Doesn't handle cyclic structures (which are forbidden by type
 *   system anyway)
 *   
 * Model:
 * - We represent relationship as DAG with all potential edges.
 * - Multiple edges with different labels  are possible between two nodes
 * - Roots of DAG are variable that is not part of anything
 *      A ----
 *     /x \y  \?
 *    B    C   D
 *   / \ /
 *  E   F
 * - Edges are labeled with either:
 * -> constant key describing relationship between child and parent, e.g.
 *    struct field name or array key
 * -> null, which means unknown or variable key
 * - We also track potential direct aliases
 */
public class ComponentGraph {
  
  private final MultiMap<Var, ComponentEdge> parents;
  private final MultiMap<Var, ComponentEdge> children;
  private final MultiMap<Var, Var> aliases;
  
  public ComponentGraph() {
    this.parents = new MultiMap<Var, ComponentEdge>();
    this.children = new MultiMap<Var, ComponentEdge>();
    this.aliases = new MultiMap<Var, Var>();
  }
  
  /**
   * Add a (potential) component relationship
   * @param child
   * @param parent enclosing structure
   * @param label a constant, or null if undetermined
   */
  public void addPotentialComponent(Var child, Var parent, Arg label) {
    assert(label == null || label.isConstant());
    parents.put(child, new ComponentEdge(parent, label));
    parents.put(parent, new ComponentEdge(child, label));
  }
  
  /**
   * Mark that the two variables potentially are the same thing
   * @param var1
   * @param var2
   */
  public void addPotentialDirectAlias(Var var1, Var var2) {
    assert(!var1.equals(var2));
    aliases.put(var1, var2);
    aliases.put(var2, var1);
  }
  
  public Set<Var> findPotentialAliases(Var var) {
    HashSet<Var> result = new HashSet<Var>();
    findPotentialAliases(var, result);
    return result;
  }
  
  public void findPotentialAliases(Var var, Set<Var> results) {
    walkUpRec(var, results, new StackLite<Pair<Var, Arg>>(),
            new HierarchicalSet<Pair<Var, Integer>>());
  }
  
  /**
   * 
   * @param var
   * @param results
   * @param pathUp track where we visited on way up
   * @param visited a set of (var, height) tuples we already visited on this
   *                 upward trip
   */
  private void walkUpRec(Var node, Set<Var> results, StackLite<Pair<Var, Arg>> pathUp,
      HierarchicalSet<Pair<Var, Integer>> visited) {
    // TODO Auto-generated method stub
    if (!pathUp.isEmpty()) {
      // Note: add all visited nodes from this root down
      walkDownRec(node, pathUp, results, visited);
    }
    
    
    // Explore all possible routes to roots
    List<ComponentEdge> parentEdges = parents.get(node);
    for (ComponentEdge parent: parentEdges) {
      pathUp.push(Pair.create(node, parent.label));
      
      HierarchicalSet<Pair<Var, Integer>> visitedParent;
      if (parentEdges.size() > 1) {
        // Use map scoping to automatically invalidate alternate paths
        visitedParent = visited.makeChild();
      } else {
        visitedParent = visited;
      }
      walkUpRec(parent.dst, results, pathUp, visitedParent);
      
      pathUp.pop();
    }

  }

  private void walkDownRec(Var node, StackLite<Pair<Var, Arg>> pathUp,
      Set<Var> results, HierarchicalSet<Pair<Var, Integer>> visited) {
    Pair<Var, Integer> visitedKey = Pair.create(node, pathUp.size());
    if (visited.contains(visitedKey)) {
      // Already visited with this pathUp: will not get new results
      return;
    }

    results.add(node);
    visited.add(visitedKey);

    // Check aliases of this.  Mark visited first to avoid cycle
    for (Var alias: aliases.get(node)) {
      walkDownRec(alias, pathUp, results, visited);
    }

    if (pathUp.isEmpty()) {
      return;
    }

    Pair<Var, Arg> prev = pathUp.pop();
    Arg childLabel = prev.val2;
    Var srcChild = prev.val1;
    
    for (ComponentEdge child: children.get(node)) {
      if (!child.dst.equals(srcChild) &&
          labelsMatch(childLabel, child.label)) {
        // Recurse if it may match and is not original path
        walkDownRec(child.dst, pathUp, results, visited);
      }
    }
    
    // Restore pathUp for caller
    pathUp.push(prev);
  }
  
  private static boolean labelsMatch(Arg label1, Arg label2) {
    if (label1 == null || label2 == null) {
      // Treat null as wildcard
      return true;
    } else {
      return label1.equals(label2);
    }
  }
    
  private static class ComponentEdge {
    final Var dst;
    final Arg label;
    
    public ComponentEdge(Var dst, Arg label) {
      assert(dst != null);
      assert(label == null || label.isConstant());
      this.dst = dst;
      this.label = label;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + dst.hashCode();
      result = prime * result + ((label == null) ? 0 : label.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      ComponentEdge other = (ComponentEdge) obj;
      if (!dst.equals(other.dst))
        return false;
      if (label == null) {
        if (other.label != null)
          return false;
      } else if (!label.equals(other.label))
        return false;
      return true;
    }
  }
  
}
