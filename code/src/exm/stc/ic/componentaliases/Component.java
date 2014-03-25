package exm.stc.ic.componentaliases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;

public class Component {
  
  /**
   * Mark "value of reference"
   */
  public static final Arg DEREF = Arg.createStringLit("*");
  
  /**
   * Enclosing object
   */
  public final Var var;
  
  /**
   * Relationship from whole to part, e.g. struct field name, or array key.
   * List element should be null or variable to represent wildcard.
   * In order from outer to inner.
   * 
   * If zero-length list, signifies that it's an alias
   */
  public final List<Arg> key;

  public Component(Var whole, List<Arg> key) {
    this.var = whole;
    this.key = key;
  }
  
  public Component(Var whole, Arg key) {
    this(whole, key.asList());
  }
  
  @Override
  public String toString() {
    return var.name() + key.toString();
  }

  public List<Component> asList() {
    return Collections.singletonList(this);
  }

  public static List<Arg> deref(List<Arg> key) {
    List<Arg> result = new ArrayList<Arg>(key.size() + 1);
    result.addAll(key);
    result.add(Component.DEREF); // Represent extra dereference
    return result;
  }
  
}
