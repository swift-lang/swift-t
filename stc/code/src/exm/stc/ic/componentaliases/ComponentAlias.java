package exm.stc.ic.componentaliases;

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
   * Enclosing object and path to component
   */
  public final Component component;
  
  /**
   * Component
   */
  public final Var alias;
  
  public ComponentAlias(Var whole, List<Arg> key, Var part) {
    this.component = new Component(whole, key);
    this.alias = part;
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
    return new ComponentAlias(ref, Component.DEREF.asList(), var);
  }

  public List<ComponentAlias> asList() {
    return Collections.singletonList(this);
  }
  
  @Override
  public String toString() {
    return "<" + component + " = " + alias + ">"; 
  }
}