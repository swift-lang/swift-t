package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.LocalForeignFunction;
import exm.stc.frontend.Context;
import exm.stc.tclbackend.TclOpTemplate.TemplateElem.ElemKind;

public class TclOpTemplate extends LocalForeignFunction {
  public static class TemplateElem {
    public static enum ElemKind {
      TEXT,
      VARIABLE,
      DEREF_VARIABLE,
      REF_VARIABLE,
    }
    
    private final ElemKind kind;
    private final String contents;
    
    private TemplateElem(ElemKind kind, String contents) {
      super();
      this.kind = kind;
      this.contents = contents;
    }
    
    public static TemplateElem createTok(String text) {
      return new TemplateElem(ElemKind.TEXT, text);
    }
    
    public static TemplateElem createVar(String varName, ElemKind kind) {
      return new TemplateElem(kind, varName);
    }
    
    public ElemKind getKind() {
      return kind;
    }
    
    public String getText() {
      if (kind == ElemKind.TEXT) {
        return contents;
      } else {
        throw new STCRuntimeError("not text, was: " + kind); 
      }
    }
    
    public String getVarName() {
      if (kind == ElemKind.VARIABLE || kind == ElemKind.DEREF_VARIABLE ||
          kind == ElemKind.REF_VARIABLE) {
        return contents;
      } else {
        throw new STCRuntimeError("not var, was: " + kind); 
      }
    }
    
    public String toString() {
      if (kind == ElemKind.VARIABLE) {
        return contents;
      } else if (kind == ElemKind.DEREF_VARIABLE) {
        return "$" + contents;
      } else if (kind == ElemKind.REF_VARIABLE) {
        return "&" + contents;
      } else {
        assert(kind == ElemKind.TEXT);
        return "\"" + contents + "\"";
      }
    }
  }

  private final ArrayList<TemplateElem> elems = 
                            new ArrayList<TemplateElem>();
  
  /**
   * Names of positional input variables for template
   */
  private final ArrayList<String> outNames =
                            new ArrayList<String>();
  
  /**
   * Names of positional output variables for template
   */
  private final ArrayList<String> inNames =
                            new ArrayList<String>();
  
  /**
   * Name of varargs (null if no varargs)
   */
  private String varArgIn = null;
  
  public boolean addInName(String e) {
    return inNames.add(e);
  }

  public boolean addInNames(Collection<? extends String> c) {
    return inNames.addAll(c);
  }

  public boolean addOutName(String e) {
    return outNames.add(e);
  }

  public boolean addOutNames(Collection<? extends String> c) {
    return outNames.addAll(c);
  }
  
  public void setVarArgIn(String varArgIn) {
    this.varArgIn = varArgIn;
  }

  public List<String> getInNames() {
    return Collections.unmodifiableList(inNames);
  }
  
  public List<String> getOutNames() {
    return Collections.unmodifiableList(outNames);
  }

  public String getVarArgIn() {
    return varArgIn;
  }

  public boolean hasVarArgs() {
    return varArgIn != null;
  }

  public void addElem(TemplateElem elem) {
    elems.add(elem);
  }
  
  public List<TemplateElem> getElems() {
    return Collections.unmodifiableList(elems);
  }

  @Override
  public String toString() {
    return elems.toString();
  }

  /**
   * Check all variables reference in template are in names or out names
   * @throws UserException 
   */
  public void verifyNames(Context context) throws UserException {
    List<String> badNames = new ArrayList<String>();
    for (TemplateElem elem: elems) {
      if (elem.getKind() == ElemKind.VARIABLE ||
          elem.getKind() == ElemKind.DEREF_VARIABLE ||
          elem.getKind() == ElemKind.REF_VARIABLE) {
        String varName = elem.getVarName();
        if (!outNames.contains(varName) && 
            !inNames.contains(varName)) {
          badNames.add(varName);
        }
      }
    }
    if (badNames.size() > 0) {
      throw UndefinedVarError.fromNames(context, badNames);
    }
  }
}