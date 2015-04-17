package exm.stc.ic.componentaliases;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.common.Logging;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.ic.ICUtil;

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
 * - Nodes may correspond to a var, or be an anonymous location
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

  /**
   * Represent wildcard component as null
   */
  private static final Arg WILDCARD = null;

  /**
   * Allocate node IDs in sequential order
   */
  private int nextNodeID;

  /**
   * Track existing nodes
   */
  private final Map<Var, Node> varNodes;

  /**
   * Track existing anonymous nodes, identified by (parent, field)
   */
  private final Map<Pair<Node, Arg>, Node> anonNodes;

  private final ListMultimap<Node, Edge> parents;
  private final ListMultimap<Node, Edge> children;
  private final ListMultimap<Node, Node> aliases;

  public ComponentGraph() {
    this.nextNodeID = 0;
    this.varNodes = new HashMap<Var, Node>();
    this.anonNodes = new HashMap<Pair<Node, Arg>, Node>();
    this.parents = ArrayListMultimap.create();
    this.children = ArrayListMultimap.create();
    this.aliases = ArrayListMultimap.create();
  }

  private static Arg normaliseComponentKey(Arg key) {
    if (key != null && !key.isConst()) {
      // Treat all non-constant keys as wildcard
      return WILDCARD;
    } else {
      return key;
    }
  }

  /**
   * Check that this is a valid component key that can be used in this
   * graph
   * @param key
   * @return
   */
  private static boolean isValidComponentKey(Arg key) {
    return key == WILDCARD || key.isConst();
  }

  /**
   * Get node corresponding to variable
   * @param var
   * @return
   */
  private Node getVarNode(Var var) {
    Node node = varNodes.get(var);
    if (node == null) {
      node = new Node(var, nextNodeID++);
      varNodes.put(var, node);
    }
    return node;
  }

  /**
   * Get anonymous node for field of variable
   * @param parent
   * @param key
   * @return
   */
  private Node getAnonNode(Node parent, Arg key) {
    assert(isValidComponentKey(key));

    // Try to avoid creating duplicates
    Pair<Node, Arg> anonKey = Pair.create(parent, key);
    Node node = anonNodes.get(anonKey);
    if (node == null) {
      node = Node.anonymous(nextNodeID++);
      anonNodes.put(anonKey, node);
    }
    return node;
  }

  /**
   * Add edge, avoiding duplicates
   * @param parents2
   * @param child
   * @param edge
   */
  private void addEdge(ListMultimap<Node, Edge> edgeLists, Node src, Edge edge) {
    // NOTE: this potentially requires linear search, but in practice lists should
    // generally be quite short
    List<Edge> edges = edgeLists.get(src);
    if (!edges.contains(edge)) {
      edgeLists.put(src, edge);
    }
  }

  public void addPotentialComponent(ComponentAlias componentAlias) {
    Var var = componentAlias.component.var;
    List<Arg> key = componentAlias.component.key;

    if (key.isEmpty()) {
      addPotentialDirectAlias(componentAlias.alias, var);
    } else {
      addPotentialComponent(var, key, componentAlias.alias);
    }
  }

  /**
   * Add a (potential) component relationship
   * @param whole enclosing structure
   * @param key relation from whole to part
   * @param part
   */
  public void addPotentialComponent(Var whole, List<Arg> key, Var part) {
    assert(!key.isEmpty());

    Node wholeNode = getVarNode(whole);
    Node partNode = getVarNode(part);

    if (logger.isTraceEnabled()) {
      logger.trace("Component: " + whole.name() + "[" + keyToString(key) + "] = "
                 + part);
    }
    // Add chain of keys from whole to part with any needed intermediate nodes
    Node curr = wholeNode;
    for (int i = 0; i < key.size(); i++) {
      Arg keyElem = normaliseComponentKey(key.get(i));

      Node child;
      if (i == key.size() - 1) {
        child = partNode;
      } else {
        child = getAnonNode(curr, keyElem);
      }

      addEdge(children, curr, new Edge(child, keyElem));
      addEdge(parents, child, new Edge(curr, keyElem));
      curr = child;
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
  public Set<Var> findPotentialAliases(Component component) {
    HashSet<Var> result = new HashSet<Var>();
    findPotentialAliases(component.var, component.key, result);
    return result;
  }

  public void findPotentialAliases(Var var, List<Arg> componentPath,
                                    Set<Var> results) {
    Node node = varNodes.get(var);

    if (node != null) {
      // Setup stack with current path
      StackLite<Pair<Node, Arg>> stack = new StackLite<Pair<Node, Arg>>();
      for (Arg component: componentPath) {
        stack.push(Pair.create((Node)null, component));
      }

      walkUpRec(node, results, stack,
                new HierarchicalSet<Pair<Node, Integer>>());
    }

    // Iteratively check all results for more aliases so we don't miss any
    // TODO: not so elegant, can result in multiple visits to nodes
    Set<Var> toProcess = new HashSet<Var>();
    toProcess.addAll(results);

    while (!toProcess.isEmpty()) {
      Set<Var> newResults = new HashSet<Var>();

      for (Var processAlias: toProcess) {
        // Locate any additional aliases of newly added var
        walkUpRec(varNodes.get(processAlias), newResults,
                  new StackLite<Pair<Node, Arg>>(),
                  new HierarchicalSet<Pair<Node, Integer>>());
      }

      toProcess.clear();
      for (Var newResult: newResults) {
        if (results.add(newResult)) {
          toProcess.add(newResult);
        }
      }
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
    if (logger.isTraceEnabled()) {
      logger.trace("walkUpRec: visit " + node + " pathUp: " + pathUp);
    }

    // Note: add all visited nodes from this root down
    walkDownRec(node, pathUp, results, visited);

    // Explore all possible routes to roots
    List<Edge> parentEdges = parents.get(node);
    for (Edge parent: parentEdges) {
      if (logger.isTraceEnabled()) {
        logger.trace("walkUpRec: visit " + parent + " from " + node);
      }
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
    if (logger.isTraceEnabled()) {
      logger.trace("walkDownRec: visit " + node + " pathUp " + pathUp);
    }

    Pair<Node, Integer> visitedKey = Pair.create(node, pathUp.size());
    if (visited.contains(visitedKey)) {
      // Already visited with this pathUp: will not get new results
      logger.trace("Already visited");
      return;
    }

    if (node.var != null) {
      results.add(node.var);
    }

    visited.add(visitedKey);

    // Check aliases of this.  Mark visited first to avoid cycle
    for (Node alias: aliases.get(node)) {
      if (logger.isTraceEnabled()) {
        logger.trace("walkDownRec: visit " + alias + " alias of " + node);
      }
      walkDownRec(alias, pathUp, results, visited);
    }

    if (pathUp.isEmpty()) {
      return;
    }

    Pair<Node, Arg> prev = pathUp.pop();
    Arg childLabel = prev.val2;
    Node srcChild = prev.val1;

    for (Edge child: children.get(node)) {
      if (logger.isTraceEnabled()) {
        logger.trace("Check child " + child + " down from " + node);
      }
      if ((srcChild == null || !child.dst.equals(srcChild)) &&
          labelsMatch(childLabel, child.label)) {
        // Recurse if it may match and is not original path
        walkDownRec(child.dst, pathUp, results, visited);
      }
    }

    // Restore pathUp for caller
    pathUp.push(prev);
  }

  private static boolean labelsMatch(Arg label1, Arg label2) {
    if (label1 == WILDCARD || label2 == WILDCARD) {
      return true;
    } else {
      return label1.equals(label2);
    }
  }

  private static String keyToString(List<Arg> key) {
    boolean first = true;
    StringBuilder sb = new StringBuilder();
    for (Arg k: key) {
      if (first) {
        first = false;
      } else {
        sb.append(".");
      }
      sb.append(keyToString(k));
    }
    return sb.toString();
  }

  private static String keyToString(Arg k) {
    if (k == WILDCARD) {
      return "?"; // Wildcard
    } else {
      return k.toString();
    }
  }

  @Override
  public String toString() {
    return "<parents:\n" + edgeListsToString(parents, "  ") + "\n" +
            "children:\n" + edgeListsToString(children, "  ") + "\n" +
           " aliases: " + aliases + ">";
  }

  private String edgeListsToString(ListMultimap<Node, Edge> graph,
                                   String indent) {
    StringBuilder sb = new StringBuilder();
    for (Entry<Node, Collection<Edge>> node: graph.asMap().entrySet()) {
      sb.append(indent);
      sb.append(node.getKey());
      sb.append(" => ");
      sb.append("[");
      ICUtil.prettyPrintList(sb, node.getValue());
      sb.append("]");
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Graph node.  Compared by id number identity
   */
  private static class Node {
    final Var var; // Variable, if variable associated with node
    public int id; // Unique ID for node, used for equality

    private Node(Var var, int id) {
      this.var = var;
      this.id = id;
    }

    public static Node anonymous(int id) {
      return new Node(null, id);
    }

    @Override
    public int hashCode() {
      return id;
    }

    @Override
    public boolean equals(Object other) {
      assert(other instanceof Node);
      // Compare only on id;
      return this.id == ((Node)other).id;
    }

    @Override
    public String toString() {
      if (var == null) {
        return Integer.toString(id);
      } else {
        return var.name();
      }
    }
  }

  private static class Edge {
    final Node dst;
    final Arg label;

    public Edge(Node dst, Arg label) {
      assert(dst != null);
      assert isValidComponentKey(label);
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

    @Override
    public String toString() {
      return "(" + keyToString(label) + ", " + dst + ")";
    }
  }
}
