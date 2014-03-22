package exm.stc.ic.componentaliases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;

/**
 * Represent a relationship where a variable is a part of another structure
 */
public class ComponentAlias {
  public static final List<ComponentAlias> NONE = Collections.emptyList();

  /**
   * Mark "value of reference"
   */
  public static final Arg DEREF = Arg.createStringLit("*");
  
  /**
   * Enclosing object
   */
  public final Var whole;
  
  /**
   * Relationship from whole to part, e.g. struct field name, or array key.
   * List element should be null or variable to represent wildcard.
   * In order from outer to inner.
   * 
   * If zero-length list, signifies that it's an alias
   */
  public final List<Arg> key;
  
  /**
   * Component
   */
  public final Var part;
  
  public ComponentAlias(Var whole, List<Arg> key, Var part) {
    this.whole = whole;
    this.part = part;
    this.key = key;
  }
  
  public ComponentAlias(Var whole, Arg key, Var part) {
    this(whole, key.asList(), part);
  }
  
  /**
   * Represent that these are aliases for one another
   * @param var1
   * @param var2
   * @return
   */
  public static ComponentAlias directAlias(Var var1, Var var2) {
    return new ComponentAlias(var2, Arg.NONE, var1);
  }

  /**
   * Constructor for reference var being dereferenced
   * @param var
   * @param ref
   * @return
   */
  public static ComponentAlias ref(Var var, Var ref) {
    return new ComponentAlias(ref, Collections.<Arg>singletonList(DEREF), var);
  }

  public static List<Arg> deref(List<Arg> key) {
    List<Arg> result = new ArrayList<Arg>(key.size() + 1);
    result.addAll(key);
    result.add(DEREF); // Represent extra dereference
    return result;
  }

  public List<ComponentAlias> asList() {
    return Collections.singletonList(this);
  }
  
  @Override
  public String toString() {
    return "<" + whole + "[" + key + "] = " + part + ">"; 
  }
}