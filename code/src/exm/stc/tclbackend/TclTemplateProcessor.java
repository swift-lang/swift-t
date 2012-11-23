package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Builtins.TclOpTemplate;
import exm.stc.common.lang.Builtins.TemplateElem;
import exm.stc.common.lang.Builtins.TemplateElem.ElemKind;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

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
      toks.put(argName, new Expression[] {new Token(outName)});
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
          es[j - i] = TclUtil.opargToExpr(inputs.get(j));
        }
        toks.put(argName, es);
      } else {
        toks.put(argName, new Expression[] {TclUtil.opargToExpr(inputs.get(i))});
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
}
