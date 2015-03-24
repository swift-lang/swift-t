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

package exm.stc.frontend;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import exm.stc.ast.FilePosition;
import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedExecContextException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.SourceLoc;
import exm.stc.common.lang.Var.VarProvenance;

/**
 * Abstract interface used to track and access contextual information about the
 * program at different points in the AST.
 */
public abstract class Context {

  public static final int ROOT_LEVEL = 0;

  /**
   * How many levels from root: 0 if this is the root
   */
  protected final int level;

  /**
   * A logger for use by child classes
   */
  protected final Logger logger;

  /**
     Map from variable name to Variable object
   */
  protected final Map<String,Var> variables = new HashMap<String,Var>();

  /**
   * Map from type name to the type object.  Most types are defined
   * in global context only, but we also have type variables with
   * restricted scope.
   */
  protected final Map<String, Type> types = new HashMap<String, Type>();

  /**
   * Track all definitions (variables and types)
   */
  protected final Map<String, DefInfo> allDefs =
                                         new HashMap<String, DefInfo>();

  /**
   * Current input file
   */
  protected String inputFile;

  /**
   * Logical name of current module
   */
  protected String moduleName;

  /**
     Current line in input file
   */
  protected int line = 0;

  /**
   * Current column in input file.  0 if unknown
   */
  protected int col = 0;

  public Context(Logger logger, int level) {
    super();
    this.level = level;
    this.logger = logger;
  }

  /**
     Return global context.
     If this is a GlobalContext, return this,
     else return the GlobalContext this is using.
   */
  public abstract GlobalContext getGlobals();

  /**
   * @return whether this is logically a top-level context
   */
  public abstract boolean isTopLevel();

  /**
   * @return global info about foreign functions
   */
  public abstract ForeignFunctions getForeignFunctions();

  /**
   * Lookup definition corresponding to name
   * @param name
   * @return the definition info, or null if not defined
   */
  public abstract DefInfo lookupDef(String name);

  /**
   * Add definition for current location in file
   * @param name
   * @param abstractType
   * @throws DoubleDefineException
   */
  protected void addDef(String name, DefKind kind)
                                    throws DoubleDefineException {
    DefInfo def = new DefInfo(kind, level, inputFile, line, col);

    checkDefConflict(name, def);

    allDefs.put(name, def);
  }

  /**
   * Check if there are any conflicting definitions for a new def
   * @param name
   * @throws DoubleDefineException
   */
  public void checkDefConflict(String name, DefInfo newDef) throws DoubleDefineException {
    DefInfo oldDef = lookupDef(name);
    /* Conflicts (e.g. shadowing, two declarations in same context) are not
     * allowed, with exceptions:
     */
    if (oldDef != null) {
      checkShadowing(name, newDef, oldDef);
    }
  }

  private void checkShadowing(String name, DefInfo newDef, DefInfo oldDef)
      throws DoubleDefineException {
    /*
     * Local variables can shadow global function and type definitions
     * without warning.  Local variables can shadow global variables but
     * this triggers a warning.
     */
    assert(newDef.level >= oldDef.level);
    if (newDef.level == oldDef.level ||
        oldDef.level != ROOT_LEVEL) {
      throw new DoubleDefineException(this, shadowMessage(name, oldDef));
    }

    if (newDef.kind == DefKind.VARIABLE &&
        (oldDef.kind == DefKind.FUNCTION || oldDef.kind == DefKind.TYPE)) {
      // This is ok
      return;
    } else {
      // This should produce warning
      LogHelper.uniqueWarn(this, shadowMessage(name, oldDef));
    }
  }

  private String shadowMessage(String name, DefInfo oldDef) {
    String loc = buildLocationString(oldDef.file, oldDef.line, oldDef.col, false);
    return oldDef.kind.humanReadable() + " called " + name +
            " already defined at " + loc;
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
   * @throws DoubleDefineException
   */
  public Var declareVariable(Type type, String name, Alloc scope,
      DefType defType, VarProvenance provenance, boolean mapped)
              throws DoubleDefineException {
    if (logger.isTraceEnabled()) {
      logger.trace("context: declareVariable: " +
                 type.toString() + " " + name + "<" + scope.toString() + ">"
                 + "<" + defType.toString() + ">");
    }

    Var variable = new Var(type, name, scope, defType, provenance, mapped);
    declareVariable(variable);
    return variable;
  }

  public Var declareVariable(Var variable)
          throws DoubleDefineException {

    if (variable.defType().isGlobal() && getGlobals() != this) {
      // Ensure globals get declared in global context
      return getGlobals().declareVariable(variable);
    }

    String name = variable.name();
    DefKind kind;
    if (Types.isFunction(variable)) {
      kind = DefKind.FUNCTION;
    } else {
      kind = DefKind.VARIABLE;
    }

    addDef(name, kind);
    variables.put(name, variable);
    return variable;
  }

  /**
   * Define a temporary variable with a unique name in the
   * current context
   * @param type
   * @param storeInStack
   * @return
   * @throws UserException
   */
  public abstract Var createTmpVar(Type type, boolean storeInStack)
  throws UserException;

  /**
   * Create a temporary variable name which will be an alias for a previously-
   * created variable (e.g. an array member).
   * @param type
   * @return
   * @throws UserException
   */
  public abstract Var createTmpAliasVar(Type type)
  throws UserException;

  /**
   * Lookup variable based on name.  This version will
   * return null if variable undeclared, leaving handling
   * to caller.
   * @param name
   * @return variable if declared, null if not declared
   */
  public abstract Var lookupVarUnsafe(String name);

  /**
   * Lookup variable based on name that is referred to
   * in user code
   * @param name
   * @return the variable
   * @throws UndefinedVarError if not found
   */
  public Var lookupVarUser(String name)
    throws UndefinedVarError {
    Var result = lookupVarUnsafe(name);
    if (result == null) {
      throw UndefinedVarError.fromName(this, name);
    }
    return result;
  }

  /**
   * Lookup variable based on name.  This version should be used in contexts
   * where the compiler has already checked the variable is declared, so if
   * the variable can't be found the problem is a bug
   * @param name
   * @return the variable
   * @throws STCRuntimeError if not found
   */
  public Var lookupVarInternal(String name)
    throws STCRuntimeError {
    Var result = lookupVarUnsafe(name);
    if (result == null) {
      throw new STCRuntimeError("Expected var " + name +
            " to already be declared at " + getLocation());
    }
    return result;
  }

  /**
   * Returns a list of all variables that are stored in the current stack
   * or an ancestor stack frame.
   * @return
   */
  public abstract List<Var> getVisibleVariables();

  public boolean isFunction(String name) {
    return lookupFunction(name) != null;
  }

  /**
   * Define function and return unique internal identifier
   * @param name
   * @param type
   * @param defaultVals
   * @return
   * @throws UserException if the definition is invalid, e.g. conflicts
   *                        with existing definition
   */
  public abstract FnID defineFunction(String name, FunctionType type,
      List<String> inArgNames, DefaultVals<Var> defaultVals) throws UserException;

  /**
   * Lookup the type of a function
   * @param name
   * @return unique identifiers and types of overloads or
   *        empty list if not defined
   */
  public abstract List<FnOverload> lookupFunction(String name);

  public abstract void setFunctionProperty(FnID id, FnProp prop);

  public abstract List<FnProp> getFunctionProps(FnID id);

  public abstract boolean hasFunctionProp(FnID id, FnProp prop);

  public abstract void addIntrinsic(FnID id, IntrinsicFunction intrinsic);

  public abstract IntrinsicFunction lookupIntrinsic(FnID id);

  public boolean isIntrinsic(FnID id) {
    return lookupIntrinsic(id) != null;
  }

  public String getInputFile() {
    return inputFile;
  }


  /**
   * Synchronize preprocessed line numbers with input file
   * line numbers
   * @param tree antlr tree for current position
   * @param moduleName logical name of current module
   * @param lineMapping map from input lines to source lines
   */
  public void syncFilePos(SwiftAST tree, String moduleName, LineMapping lineMapping) {
    // Sometime antlr nodes give bad line info - negative numbers
    if (tree.getLine() > 0) {
      FilePosition pos = lineMapping.getFilePosition(tree.getLine());
      this.inputFile = pos.file;
      this.moduleName = moduleName;
      this.line = pos.line;
      this.col = tree.getCharPositionInLine();
    }
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return col;
  }

  /**
     @return E.g.; "path/file.txt:42"
   */
  public String getFileLine() {
    return getInputFile() + ":" + getLine();
  }

  /**
     @return E.g.; "file.txt:42: "
   */
  public String getLocation() {
    return buildLocationString(getInputFile(), getLine(), getColumn(), true);
  }

  public SourceLoc getSourceLoc() {
    String function;
    FunctionContext fc = getFunctionContext();
    if (fc == null || isTopLevel()) {
      function = null;
    } else {
      function = fc.getFunctionName();
    }

    return new SourceLoc(getInputFile(), moduleName, function, getLine(), getColumn());
  }

  /**
   * Build a human-readable location string
   * @param inputFile
   * @param line
   * @param col
   * @return
   */
  private String buildLocationString(String inputFile, int line, int col,
                              boolean trailingColon) {
    String res = new File(inputFile).getName() + ":" + line;
    if (col > 0) {
      res += ":" + (col + 1);
    }
    if (trailingColon) {
      res += ": ";
    }
    return res;
  }

  public final int getLevel() {
    return level;
  }

  public final Logger getLogger() {
    return logger;
  }

  /**
   * @return the variables which were declared in this scope
   */
  public Collection<Var> getScopeVariables() {
    return Collections.unmodifiableCollection(variables.values());
  }

  /**
   * @param typeName
   * @return type corresponding to name, or otherwise null
   */
  abstract public Type lookupTypeUnsafe(String typeName);

  /**
   * @param typeName
   * @return type corresponding to name
   * @throws UndefinedTypeException
   * @throw UndefinedTypeException if type is not defined
   */
  public Type lookupTypeUser(String typeName) throws UndefinedTypeException {
    Type t = lookupTypeUnsafe(typeName);
    if (t == null) {
      throw new UndefinedTypeException(this, typeName);
    } else {
      return t;
    }
  }

  public void defineType(String typeName, Type newType)
      throws DoubleDefineException {
    addDef(typeName, DefKind.TYPE);
    types.put(typeName, newType);
  }

  /**
   * Lookup execution target by name in global scope.
   * @param name
   * @return
   */
  public abstract ExecContext lookupExecContext(String name)
                    throws UndefinedExecContextException;

  /**
   * @return valid names of execution targets
   */
  public abstract Collection<String> execTargetNames();

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

  abstract public Var createStructFieldTmp(Var struct,
      Type fieldType, List<String> fieldPath, Alloc storage);

  abstract public Var createStructFieldTmpVal(Var struct,
      Type fieldType, List<String> fieldPath, Alloc storage);

  /** Get info about the enclosing function */
  abstract public FunctionContext getFunctionContext();

  /** Get counters at function or global level */
  abstract public long nextCounterVal(String counterName);

  /**
   * Shortcut method for getFunctionContext().constructName()
   * @param constructType
   * @return
   */
  public String constructName(String constructType) {
    return getFunctionContext().constructName(constructType);
  }

  /**
   * Helper to choose variable name.
   * @param prefix Prefix that must be at start
   * @param preferredSuffix Preferred suffix
   * @param counterName name of counter to use to make unique if needed
   * @return
   */
  protected String uniqueName(String prefix, String preferredSuffix,
      String counterName) {
    if (preferredSuffix != null) {
      prefix += preferredSuffix;
      // see if we can give it a nice name
      if (lookupDef(prefix) == null) {
        return prefix;
      }
    }

    String name = null;
    do {
      long counter = nextCounterVal(counterName);
      name = prefix + counter;
    } while (lookupDef(name) != null);
    return name;
  }
  /**
   *
   * @param type
   * @param var future this is the value of
   * @return
   * @throws UserException
   */
  abstract public Var createLocalValueVariable(Type type, Var var)
                                               throws UserException;

  public Var createLocalValueVariable(Type type)
        throws UserException {
    return createLocalValueVariable(type, null);
  }

  /**
   * Create filename alias variable (a string future) for a file
   * variable with provided name
   * @param fileVar file variable
   * @return
   */
  abstract public Var createFilenameAliasVariable(Var fileVar);

  /**
   * Create a global const variable
   * @param context
   * @param name name for global const, or null if name should be
   *             automatically generated
   * @param type
   * @param makeUnique if true, generate a unique name.  If false, use provided
   * @return
   * @throws DoubleDefineException
   */
  public abstract Var createGlobalConst(String name, Type type,
                              boolean makeUnique) throws DoubleDefineException;

  public static enum FnProp {
    APP, COMPOSITE,
    BUILTIN, SYNC,
    WRAPPED_BUILTIN,
    PARALLEL, /** if this is a parallel task */
    TARGETABLE, /** if this is targetable */
    DEPRECATED, /** Warn if user uses function */
    CHECKPOINTED, /** Whether results should be checkpointed */
  }

  /**
   * Different types of definition name can be associated with.
   */
  public static enum DefKind {
    FUNCTION, VARIABLE, TYPE;

    public String humanReadable() {
      return this.toString().toLowerCase();
    }
  }

  /**
   * Information about a definition
   */
  public static class DefInfo {
    public DefInfo(DefKind kind, int level, String file, int line, int col) {
      this.kind = kind;
      this.level = level;
      this.file = file;
      this.line = line;
      this.col = col;
    }
    public final DefKind kind;
    public final int level; /* Context level */
    public final String file;
    public final int line;
    public final int col;
  }

  public static class FnOverload {
    public final FnID id;
    public final FunctionType type;
    public final List<String> inArgNames;
    public final DefaultVals<Var> defaultVals;

    public FnOverload(FnID id, FunctionType type, List<String> inArgNames,
                      DefaultVals<Var> defaultVals) {
      this.id = id;
      this.type = type;
      this.inArgNames = Collections.unmodifiableList(
                                    new ArrayList<String>(inArgNames));
      this.defaultVals = defaultVals;
    }

    public List<FnOverload> asList() {
      return Collections.singletonList(this);
    }

    @Override
    public String toString() {
      return "FnOverload: " + id + " " + type + " " + defaultVals;
    }
  }
}
