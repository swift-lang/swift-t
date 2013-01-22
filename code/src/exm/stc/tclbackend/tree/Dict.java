/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
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
