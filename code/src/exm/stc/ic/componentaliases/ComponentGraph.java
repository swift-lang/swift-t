package exm.stc.ic.componentaliases;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
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
  Logger logger = Logging.getSTCLogger();
  
  private final Map<Var, Node> varNodes;
  private final MultiMap<Node, Edge> parents;
  private final MultiMap<Node, Edge> children;
  private final MultiMap<Node, Node> aliases;
  
  public ComponentGraph() {
    this.varNodes = new HashMap<Var, Node>();
    this.parents = new MultiMap<Node, Edge>();
    this.children = new MultiMap<Node, Edge>();
    this.aliases = new MultiMap<Node, Node>();
  }
  
  /**
   * Get node corresponding to variable
   * @param var
   * @return
   */
  private Node getVarNode(Var var) {
    Node node = varNodes.get(var);
    if (node == null) {
      node = new Node(var);
      varNodes.put(var, node);
    }
    return node;
  }
  
  public void addPotentialComponent(ComponentAlias componentAlias) {
    if (componentAlias.key.isEmpty()) {
      addPotentialDirectAlias(componentAlias.part, componentAlias.whole);
    } else {
      addPotentialComponent(componentAlias.part, componentAlias.whole,
                            componentAlias.key);
    }
  }

  /**
   * Add a (potential) component relationship
   * @param part
   * @param whole enclosing structure
   * @param key relation from whole to part
   */
  public void addPotentialComponent(Var part, Var whole, List<Arg> key) {
    assert(!key.isEmpty());
    
    Node wholeNode = getVarNode(whole);
    Node partNode = getVarNode(part);
    
    
    // Add chain of keys with any needed intermediate nodes
    Node curr = partNode;
    for (int i = 0; i < key.size(); i++) {
      Arg keyElem = key.get(i);
      if (keyElem != null && !keyElem.isConstant()) {
        // Treat all non-constant keys as wildcard
        keyElem = null;
      }
      assert(keyElem == null || keyElem.isConstant());
      
      Node parent;
      if (i == key.size() - 1) {
        parent = wholeNode;
      } else {
        parent = Node.anonymous();
      }
      
      parents.put(curr, new Edge(parent, keyElem));
      children.put(parent, new Edge(curr, keyElem));
    }
  }
  
  /**
   * Mark that the two variables potentially are the same thing
   * @param var1
   * @param var2
   */
  public void addPotentialDirectAlias(Var var1, Var var2) {
    assert(!var1.equals(var2));
    Node node1 = getVarNode(var1);
    Node node2 = getVarNode(var2);
    aliases.put(node1, node2);
    aliases.put(node2, node1);
  }
  
  /**
   * Find variables that this variable may potential alias a part of
   * @param var
   * @return
   */
  public Set<Var> findPotentialAliases(Var var) {
    HashSet<Var> result = new HashSet<Var>();
    findPotentialAliases(var, result);
    return result;
  }
  
  public void findPotentialAliases(Var var, Set<Var> results) {
    Node node = varNodes.get(var);
    if (node != null) { 
      walkUpRec(node, results, new StackLite<Pair<Node, Arg>>(),
              new HierarchicalSet<Pair<Node, Integer>>());
    }
  }
  
  /**
   * 
   * @param var
   * @param results
   * @param pathUp track where we visited on way up
   * @param visited a set of (var, height) tuples we already visited on this
   *                 upward trip
   */
  private void walkUpRec(Node node, Set<Var> results, StackLite<Pair<Node, Arg>> pathUp,
      HierarchicalSet<Pair<Node, Integer>> visited) {
    if (!pathUp.isEmpty()) {
      // Note: add all visited nodes from this root down
      walkDownRec(node, pathUp, results, visited);
    }
    
    // Explore all possible routes to roots
    List<Edge> parentEdges = parents.get(node);
    for (Edge parent: parentEdges) {
      pathUp.push(Pair.create(node, parent.label));
      
      HierarchicalSet<Pair<Node, Integer>> visitedParent;
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

  private void walkDownRec(Node node, StackLite<Pair<Node, Arg>> pathUp,
      Set<Var> results, HierarchicalSet<Pair<Node, Integer>> visited) {
    Pair<Node, Integer> visitedKey = Pair.create(node, pathUp.size());
    if (visited.contains(visitedKey)) {
      // Already visited with this pathUp: will not get new results
      return;
    }

    if (node.var != null) {
      results.add(node.var);
    }
    
    visited.add(visitedKey);

    // Check aliases of this.  Mark visited first to avoid cycle
    for (Node alias: aliases.get(node)) {
      walkDownRec(alias, pathUp, results, visited);
    }

    if (pathUp.isEmpty()) {
      return;
    }

    Pair<Node, Arg> prev = pathUp.pop();
    Arg childLabel = prev.val2;
    Node srcChild = prev.val1;
    
    for (Edge child: children.get(node)) {
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
  
  @Override
  public String toString() {
    return "<parents: " + parents + " children: " + children +
           " aliases: " + aliases + ">";
  }
  
  /**
   * Graph node.  Compared by object identity
   */
  private static class Node {
    final Var var; // Variable, if variable associated with node

    public Node(Var var) {
      this.var = var;
    }

    public static Node anonymous() {
      return new Node(null);
    }
  }
    
  private static class Edge {
    final Node dst;
    final Arg label;
    
    public Edge(Node dst, Arg label) {
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
      Edge other = (Edge) obj;
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
