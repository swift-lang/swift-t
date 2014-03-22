package exm.stc.ic.componentaliases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;

/**
 * Represent a relationship where a variable is a part of another structure
 */
public class ComponentAlias {
  public static final List<ComponentAlias> NONE = Collections.emptyList();
  
  /**
   * Enclosing object
   */
  public final Var whole;
  
  /**
   * Component
   */
  public final Var part;
  
  /**
   * Relationship from whole to part, e.g. struct field name, or array key.
   * List element should be null or variable to represent wildcard.
   * In order from outer to inner
   */
  public final List<Arg> key;
  
  public ComponentAlias(Var part, Var whole, List<Arg> key) {
    this.whole = whole;
    this.part = part;
    this.key = key;
  }
  
  public ComponentAlias(Var part, Var whole, Arg key) {
    this(part, whole, key.asList());
  }
  
  /**
   * Represent that these are aliases for one another
   * @param var1
   * @param var2
   * @return
   */
  public static ComponentAlias directAlias(Var var1, Var var2) {
    // TODO
    throw new STCRuntimeError("not implemented");
  }

  /**
   * Constructor for reference var being dereferenced
   * @param var
   * @param ref
   * @return
   */
  public static ComponentAlias ref(Var var, Var ref) {
    // Only one field so can use wildcard
    return new ComponentAlias(var, ref, Collections.<Arg>singletonList(null));
  }

  public static List<Arg> deref(List<Arg> key) {
    List<Arg> result = new ArrayList<Arg>(key.size() + 1);
    result.addAll(key);
    result.add(null); // Represent extra dereference
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