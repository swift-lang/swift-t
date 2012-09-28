package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.lang.Variable;

public class TclNamer {
  /** to avoid clashes with other tcl variables, prefix variables/functions
   * names from swift with these prefixes:
   */
  private static final String FN_PREFIX = "f:";
  private static final String TCL_TMP_VAR_PREFIX = "t:";
  private static final String TCL_ALIAS_VAR_PREFIX = "a:";
  private static final String TCL_USER_VAR_PREFIX = "u:";
  private static final String TCL_VALUE_VAR_PREFIX = "v:";
  private static final String TCL_OPT_VALUE_VAR_PREFIX = "optv:";
  private static final String TCL_STRUCT_FIELD_VAR_PREFIX = "sf:";
  private static final String TCL_LOOPINDEX_VAR_PREFIX = "i:";
  private static final String TCL_GLOBAL_CONST_VAR_PREFIX = "c:";
  private static final String TCL_DEREF_COMPILER_VAR_PREFIX = "dr:";
  private static final String TCL_LOOP_INDEX_VAR_PREFIX = "lv:";
  private static final String TCL_OUTER_VAR_PREFIX = "outer:";
  
  public static String prefixVar(String varname) {
    // Replace the internal names of temporary variables with
    // shorter ones for generated tcl code
    if (varname.startsWith(Variable.TMP_VAR_PREFIX)) {
      return TCL_TMP_VAR_PREFIX + varname.substring(
              Variable.TMP_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.ALIAS_VAR_PREFIX)) {
      return TCL_ALIAS_VAR_PREFIX + varname.substring(Variable.ALIAS_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.STRUCT_FIELD_VAR_PREFIX)) {
      return TCL_STRUCT_FIELD_VAR_PREFIX + varname.substring(
          Variable.STRUCT_FIELD_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.LOCAL_VALUE_VAR_PREFIX))  {
      return TCL_VALUE_VAR_PREFIX +
          varname.substring(Variable.LOCAL_VALUE_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.OPT_VALUE_VAR_PREFIX))  {
      return TCL_OPT_VALUE_VAR_PREFIX +
          varname.substring(Variable.OPT_VALUE_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.LOOP_INDEX_VAR_PREFIX)) {
      return TCL_LOOPINDEX_VAR_PREFIX +
          varname.substring(Variable.LOOP_INDEX_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.GLOBAL_CONST_VAR_PREFIX)) {
      return TCL_GLOBAL_CONST_VAR_PREFIX +
          varname.substring(Variable.GLOBAL_CONST_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.DEREF_COMPILER_VAR_PREFIX)) {
      return TCL_DEREF_COMPILER_VAR_PREFIX +
          varname.substring(Variable.DEREF_COMPILER_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.LOOP_INDEX_VAR_PREFIX)) {
      return TCL_LOOP_INDEX_VAR_PREFIX +
          varname.substring(Variable.LOOP_INDEX_VAR_PREFIX.length());
    } else if (varname.startsWith(Variable.OUTER_VAR_PREFIX)) {
      return TCL_OUTER_VAR_PREFIX +
          varname.substring(Variable.OUTER_VAR_PREFIX.length());

    } else {
      return TCL_USER_VAR_PREFIX + varname;
    }
  }

  public static List<String> prefixVars(List<String> vlist) {
    ArrayList<String> result = new ArrayList<String>(vlist.size());
    for (String v: vlist) {
      result.add(prefixVar(v));
    }
    return result;
  }

  public static String swiftFuncName(String function) {
    return FN_PREFIX + function;
  }
}
