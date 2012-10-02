
package exm.stc.frontend;

import java.io.File;
import java.util.*;

import org.apache.log4j.Logger;

import exm.stc.ast.FilePosition;
import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable.DefType;
import exm.stc.common.lang.Variable.VariableStorage;

/**
 * Abstract interface used to track and access contextual information about the
 * program at different points in the AST. 
 */
public abstract class Context {
  protected int level = 0;

  protected Logger logger = null;

  /**
     Map from variable name to Variable object
   */
  protected Map<String,Variable> variables = new HashMap<String,Variable>();

  /**
     True if this context scope has a visible parent scope
   */
  protected boolean nested = false;

  /**
     Current line in input file
   */
  protected int line = 0;

  /**
     Return global context.
     If this is a GlobalContext, return this,
     else return the GlobalContext this is using.
   */
  public abstract GlobalContext getGlobals();
  
  /**
   * Lookup definition corresponding to name
   * @param name
   * @return
   */
  public DefKind lookupDef(String name) {
    if (lookupType(name) != null) {
      return DefKind.TYPE;
    } else if (getDeclaredVariable(name) != null) {
      return DefKind.VARIABLE;
    } else {
      return null;
    }
  }
  
  public void checkNotDefined(String name) throws DoubleDefineException {
    DefKind def = lookupDef(name);
    if (def != null) {
      throw new DoubleDefineException(this, def.toString().toLowerCase() + 
          " called " + name + " already defined");
    }
  }
  
  /**
   * Declare a new variable that will be visible in the
   * current scope and all descendant scopes
   * @param type
   * @param name
   * @param scope
   * @param defType
   * @param mapping
   * @return
   * @throws UserException
   */
  public abstract Variable declareVariable(SwiftType type, String name, VariableStorage scope,
      DefType defType, Variable mapping) throws UserException;

  /**
   * Flag that an array should have its writers count decremented at
   * end of block.  Multiple calls for same variable will result
   * in duplicates
   * @param var
   */
  public abstract void flagArrayForClosing(Variable var);
  
  /**
   * Get list of all arrays that were flagged
   * @return
   */
  public abstract List<Variable> getArraysToClose();

  /**
   * Define a temporary variable with a unique name in the
   * current context
   * @param type
   * @param storeInStack
   * @return
   * @throws UserException
   */
  public abstract Variable createTmpVar(SwiftType type, boolean storeInStack)
  throws UserException;

  /**
   * Create a temporary variable name which will be an alias for a previously-
   * created variable (e.g. an array member).
   * @param type
   * @return
   * @throws UserException
   */
  public abstract Variable createAliasVariable(SwiftType type)
  throws UserException;

  /**
   * Lookup variable based on name
   * @param name
   * @return
   */
  public abstract Variable getDeclaredVariable(String name);

  /**
   * Returns a list of all variables that are stored in the current stack
   * or an ancestor stack frame.
   * @return
   */
  public abstract List<Variable> getVisibleVariables();

  public boolean isFunction(String name) {
    return lookupFunction(name) != null;
  }

  public abstract void defineFunction(String name, FunctionType type)
                                          throws DoubleDefineException;
  
  public abstract void setFunctionProperty(String name, FnProp prop);
  
  public abstract boolean hasFunctionProp(String name, FnProp prop);
  
  /**
   * Lookup the type of a function
   * @param name
   * @return
   */
  public FunctionType lookupFunction(String name) {
    Variable var = getDeclaredVariable(name);
    if (var == null || !Types.isFunction(var.getType())) {
      return null;
    }
    return (FunctionType)var.getType();
  }

  public void setNested(boolean b)
  {
    nested = b;
  }

  public boolean isNested()
  {
    return nested;
  }

  /**
    Set the line of the current input file
    Used by CPP linemarkers
     For annotations, debugging, and error messages
   */
  public abstract void setInputFile(String file);

  public abstract String getInputFile();

  
  /**
   * Synchronize preprocessed line numbers with input file
   * line numbers
   * @param context
   * @param antlrLine
   * @param lineMapping
   */
  public void syncFileLine(int antlrLine, LineMapping lineMapping) {
    // Sometime antlr nodes give bad line info - negative numbers
    if (antlrLine > 0) {
      FilePosition pos = lineMapping.getFilePosition(antlrLine);
      setInputFile(pos.file);
      this.line = pos.line;
    }
  }

  public int getLine() {
    return line;
  }

  /**
     @return E.g.; "path/file.txt:42"
   */
  public String getFileLine()
  {
    return getInputFile() + ":" + getLine();
  }

  /**
     @return E.g.; "file.txt:42: "
   */
  public String getLocation()
  {
    return getInputFileBasename() + ":" + getLine() + ": ";
  }

  public String getInputFileBasename()
  {
    return new File(getInputFile()).getName();
  }

  public int getLevel()
  {
    return level;
  }

  public Logger getLogger()
  {
    return logger;
  }

  /**
   * @return the variables which were declared in this scope
   */
  public Collection<Variable> getScopeVariables() {
    return Collections.unmodifiableCollection(variables.values());
  }

  abstract public SwiftType lookupType(String typeName);

  abstract public void defineType(String typeName, SwiftType newType)
    throws DoubleDefineException;

  public abstract Map<String, SwiftType> getCurrentTypeMapping();

  protected String buildPathStr(List<String> fieldPath) {
    StringBuilder build = new StringBuilder();
    for (String field: fieldPath) {
      if (build.length() > 0) {
        build.append('.');
      }
      build.append(field);
    }
    return build.toString();
  }

  abstract protected Variable createStructFieldTmp(Variable struct,
      SwiftType fieldType, String fieldPath, VariableStorage storage);

  public Variable createStructFieldTmp(Variable struct,
      SwiftType fieldType, List<String> fieldPath, VariableStorage storage) {
    String pathStr = buildPathStr(fieldPath);
    return createStructFieldTmp(struct, fieldType, pathStr, storage);
  }
  
  /** Get info about the enclosing function */
  abstract public FunctionContext getFunctionContext();

  /**
   * 
   * @param type
   * @param varName name of future this is the value of
   * @return
   * @throws UserException
   */
  abstract public Variable createLocalValueVariable(SwiftType type,
      String varName) throws UserException;

  public Variable createLocalValueVariable(SwiftType type) 
        throws UserException {
    return createLocalValueVariable(type, null);
  }
  
  /**
   * Create filename alias variable (a string future) for a file
   * variable with provided name
   * @param name name of file variable
   * @return
   */
  abstract public Variable createFilenameAliasVariable(String name);
  
  public static enum FnProp {
    APP, COMPOSITE, BUILTIN, SYNC;
  }
  
  /**
   * Different types of definition name can be associated with.
   */
  public static enum DefKind {
    FUNCTION, VARIABLE, TYPE;
  }
}
