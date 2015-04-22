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
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.StringUtil;
import exm.stc.tclbackend.TclOpTemplate.TemplateElem;
import exm.stc.tclbackend.TclOpTemplate.TemplateElem.ElemKind;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

public class TclTemplateProcessor {
  /**
   * Argument passed into template
   */
  public static class TemplateArg {
    /** Expression - must be provided */
    public final Expression expr;

    /** Whether output */
    public final boolean isOutput;

    /** If output, pass it by name always? */
    public final boolean passByName;

    /** Variable name if it is a variable */
    public final String varName;

    private TemplateArg(Expression expr, boolean isOutput, boolean passByName,
                       String varName) {
      assert(expr != null);
      assert(!isOutput || varName != null) : "Outputs must include var name";
      this.varName = varName;
      this.isOutput = isOutput;
      this.passByName = passByName;
      this.expr = expr;
    }

    public static TemplateArg fromInputArg(Arg arg) {
      assert(arg != null);
      String varName = arg.isVar() ? arg.getVar().name() : null;
      return createInput(varName, TclUtil.argToExpr(arg));
    }

    public static TemplateArg createInput(String varName, Expression expr) {
      return new TemplateArg(expr, false, false, varName);
    }

    public static TemplateArg createOutput(String varName, boolean passByName,
                                            Expression expr) {
      return new TemplateArg(expr, true, passByName, varName);
    }

    public static TemplateArg fromOutputVar(Var output) {
      return createOutput(output.name(), TclUtil.passOutByName(output.type()),
                          TclUtil.varToExpr(output));
    }
  }

  public static List<TclTree> processTemplate(
      String context, TclOpTemplate template,
      List<TemplateArg> inputs, List<TemplateArg> outputs) {
    // First work out what variable names map to what args
    ListMultimap<String, TemplateArg> argMap;
    argMap = buildArgMap(template, inputs, outputs);

    ArrayList<TclTree> result = new ArrayList<TclTree>();

    // Now fill in template
    for (TemplateElem elem: template.getElems()) {
      if (elem.getKind() == ElemKind.TEXT) {
        String tok = StringUtil.tclTrim(elem.getText());
        if (tok.length() > 0) {
          result.add(new Token(tok));
        }
      } else {
        List<TemplateArg> argVals = argMap.get(elem.getVarName());
        for (TemplateArg argVal: argVals) {
          result.add(getExpr(context, elem.getVarName(),
                             elem.getKind(), argVal));
        }
      }
    }
    return result;
  }

  private static ListMultimap<String, TemplateArg> buildArgMap(TclOpTemplate template,
      List<TemplateArg> inputs, List<TemplateArg> outputs) {
    // May be multiple tokens per output
    ListMultimap<String, TemplateArg> toks = ArrayListMultimap.create();

    List<String> outNames = template.getOutNames();
    for (int i = 0; i < outputs.size(); i++) {
      TemplateArg out = outputs.get(i);
      assert(out.isOutput);

      String argName = outNames.get(i);
      toks.put(argName, out);
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
        for (int j = i; j < inputs.size(); j++) {
          TemplateArg input = inputs.get(j);
          assert(!input.isOutput);
          toks.put(argName, input);
        }
      } else {
        TemplateArg input = inputs.get(i);
        assert(!input.isOutput);
        toks.put(argName, input);
      }
    }
    return toks;
  }

  private static Expression getExpr(String context, String templateParamName,
              ElemKind kind, TemplateArg arg) {
    if (kind == ElemKind.DEREF_VARIABLE ||
        (kind == ElemKind.VARIABLE && !arg.isOutput) ||
        (kind == ElemKind.VARIABLE && arg.isOutput &&
                  !arg.passByName)) {
      return arg.expr;
    } else if (kind == ElemKind.REF_VARIABLE ||
            (kind == ElemKind.VARIABLE && arg.isOutput &&
                  arg.passByName)) {
      if (arg.varName == null) {
        throw new STCRuntimeError("Cannot pass constant argument " + arg
                + " as variable name to template parameter for "
                + context + " argument " + templateParamName);
      }
      return new Token(TclNamer.prefixVar(arg.varName));
    } else {
      throw new STCRuntimeError("Should not reach here: conditions " +
      		"not exhaustive");
    }
  }
}
