package exm.stc.tclbackend.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import exm.stc.common.util.Pair;

public class Dict extends Square {
  /**
   * Hidden constructor: use static factory methods to
   * construct valid opbjects
   * @param tokens
   */
  private Dict(Expression... tokens) {
    super(tokens);
  }


  public static Dict dictCreateSS(
      Collection<Pair<String, String>> elems) {
    List<Pair<Expression, Expression>> exprs =
      new ArrayList<Pair<Expression, Expression>>(elems.size());
    for (Pair<String, String> elem: elems) {
      exprs.add(Pair.<Expression, Expression>create(
            new TclString(elem.val1, true), new TclString(elem.val2, true)));
    }
    return dictCreate(exprs);
  }
  
  
  public static Dict dictCreateSE(
      Collection<Pair<String, Expression>> elems) {
    List<Pair<Expression, Expression>> exprs =
      new ArrayList<Pair<Expression, Expression>>(elems.size());
    for (Pair<String, Expression> elem: elems) {
      exprs.add(Pair.<Expression, Expression>create(
                  new TclString(elem.val1, true), elem.val2));
    }
    return dictCreate(exprs);
  }
  
  public static Dict dictCreate(
        Collection<Pair<Expression, Expression>> elems) {
    Expression exprs[] = new Expression[elems.size() * 2 + 2];
    exprs[0] = new Token("dict");
    exprs[1] = new Token("create");
    int pos = 2;
    for (Pair<? extends Expression, Expression> elem: elems) {
      exprs[pos++] = elem.val1;
      exprs[pos++] = elem.val2;
    }
    
    return new Dict(exprs);
  }
}
