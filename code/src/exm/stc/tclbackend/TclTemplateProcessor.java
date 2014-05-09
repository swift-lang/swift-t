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

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.util.StringUtil;
import exm.stc.tclbackend.TclOpTemplate.TemplateElem;
import exm.stc.tclbackend.TclOpTemplate.TemplateElem.ElemKind;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

public class TclTemplateProcessor {
  
  private static class ArgSub {
    private ArgSub(boolean isOutput, Arg ...args) {
      super();
      this.args = args;
      this.isOutput = isOutput;
    }
    public final Arg args[];
    public final boolean isOutput;
  }
  
  public static List<TclTree> processTemplate(
      String funcName, TclOpTemplate template,
      List<Arg> inputs, List<Var> outputs) {
    // First work out what variable names map to what args 
    HashMap<String, ArgSub> argMap = buildArgMap(template, inputs, outputs);
    
    ArrayList<TclTree> result = new ArrayList<TclTree>();
    
    // Now fill in template 
    for (TemplateElem elem: template.getElems()) {
      if (elem.getKind() == ElemKind.TEXT) {
        String tok = StringUtil.tclTrim(elem.getText());
        if (tok.length() > 0) {
          result.add(new Token(tok));
        }
      } else {
        ArgSub sub = argMap.get(elem.getVarName());
        for (Arg arg: sub.args) {
          result.add(getExpr(funcName, elem.getVarName(), 
                            sub.isOutput, elem.getKind(), arg));
        }
      }
    }
    return result;
  }

  private static HashMap<String, ArgSub> buildArgMap(TclOpTemplate template,
      List<Arg> inputs, List<Var> outputs) {
    HashMap<String, ArgSub> toks = new HashMap<String, ArgSub>();
    
    List<String> outNames = template.getOutNames();
    for (int i = 0; i < outputs.size(); i++) {
      Var out = outputs.get(i);
      String argName = outNames.get(i);
      toks.put(argName, new ArgSub(true, out.asArg()));
    }
   
    List<String> inNames = template.getInNames();
    if (template.hasVarArgs()) {
      assert(inputs.size() >= inNames.size() - 1) : inputs + " " + inNames;
    } else {
      assert(inNames.size() == inputs.size()) : inputs + " " + inNames;
    }
    for (int i = 0; i < inNames.size(); i++) {
      String argName = inNames.get(i);
      if (template.hasVarArgs() && i == inNames.size() - 1) {
        // Last argument: varargs
        Arg as[] = new Arg[inputs.size() - inNames.size() + 1];
        for (int j = i; j < inputs.size(); j++) {
          as[j - i] = inputs.get(j);
        }
        toks.put(argName, new ArgSub(false, as));
      } else {
        toks.put(argName, new ArgSub(false, inputs.get(i)));
      }
    }
    return toks;
  }

  private static Expression getExpr(String funcName, String varName,
              boolean isOutput, ElemKind kind, Arg arg) {
    if (kind == ElemKind.DEREF_VARIABLE ||
        (kind == ElemKind.VARIABLE && !isOutput) ||
        (kind == ElemKind.VARIABLE && isOutput &&
                  !passOutByName(arg.type()))) {
      return TclUtil.argToExpr(arg);
    } else if (kind == ElemKind.REF_VARIABLE || 
            (kind == ElemKind.VARIABLE && isOutput &&
             passOutByName(arg.type()))) {
      if (!arg.isVar()) {
        throw new STCRuntimeError("Cannot pass constant argument " + arg 
                + " as variable name to template parameter for function "
                + funcName + " argument " + varName);
      }
      String tclVarName = TclNamer.prefixVar(arg.getVar().name());
      return new Token(tclVarName);
    } else {
      throw new STCRuntimeError("Should not reach here: conditions " +
      		"not exhaustive");
    }
  }

  private static boolean passOutByName(Type type) {
    if (Types.isContainer(type)) {
      // Pass Turbine arrays by type
      return false;
    }
    return true;
  }
}
