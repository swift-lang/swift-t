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
package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Builtins.TclOpTemplate;
import exm.stc.common.lang.Builtins.TemplateElem;
import exm.stc.common.lang.Builtins.TemplateElem.ElemKind;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;
import exm.stc.tclbackend.tree.Value;

public class TclTemplateProcessor {
  public static List<TclTree> processTemplate(TclOpTemplate template,
      List<Arg> inputs, List<Var> outputs) {
    // First work out values for different names
    HashMap<String, Expression[]> toks = new HashMap<String, Expression[]>();
    
    List<String> outNames = template.getOutNames();
    for (int i = 0; i < outputs.size(); i++) {
      Var out = outputs.get(i);
      String argName = outNames.get(i);
      String outName = TclNamer.prefixVar(out.name());
      Expression expr;
      if (passOutByName(out.type())) {
        expr = new Token(outName);
      } else {
        expr = new Value(outName);
      }
      toks.put(argName, new Expression[] {expr});
    }
   
    List<String> inNames = template.getInNames();
    if (template.hasVarArgs()) {
      assert(inputs.size() >= inNames.size() - 1);
    } else {
      assert(inNames.size() == inputs.size());
    }
    for (int i = 0; i < inNames.size(); i++) {
      String argName = inNames.get(i);
      if (template.hasVarArgs() && i == inNames.size() - 1) {
        // Last argument: varargs
        Expression es[] = new Expression[inputs.size() - inNames.size() + 1];
        for (int j = i; j < inputs.size(); j++) {
          es[j - i] = TclUtil.argToExpr(inputs.get(j));
        }
        toks.put(argName, es);
      } else {
        toks.put(argName, new Expression[] {TclUtil.argToExpr(inputs.get(i))});
      }
    }
    
    ArrayList<TclTree> result = new ArrayList<TclTree>();
    
    // Now fill in template 
    for (TemplateElem elem: template.getElems()) {
      if (elem.getKind() == ElemKind.TEXT) {
        String tok = elem.getText().trim();
        if (tok.length() > 0) {
          result.add(new Token(tok));
        }
      } else {
        assert(elem.getKind() == ElemKind.VARIABLE);
        for (Expression e: toks.get(elem.getVarName())) {
          result.add(e);
        }
      }
    }
    return result;
  }

  private static boolean passOutByName(Type type) {
    if (Types.isArray(type)) {
      // Pass Turbine arrays by type
      return true;
    }
    return true;
  }
}
