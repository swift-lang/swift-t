package exm.stc.frontend;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.ModuleLoadException;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.frontend.tree.Literals;

public class LoadedModules {

  /** Stack of input files.  Top of stack is one currently processed */
  private StackLite<ParsedModule> moduleStack = new StackLite<ParsedModule>();

  /** Map of canonical name to input file for all already loaded */
  private Map<String, ParsedModule> loadedModuleMap =
                        new HashMap<String, ParsedModule>();

  /** List of modules in order of inclusion */
  private List<LocatedModule> loadedModules = new ArrayList<LocatedModule>();

  /** List of modules we've started compiling top level of */
  private Set<ParsedModule> startedTopLevelCompile =
              new HashSet<ParsedModule>();

  public List<LocatedModule> loadedModules() {
    return Collections.unmodifiableList(loadedModules);
  }

  /**
   *
   * @param module
   * @return The loaded module, and true if we freshly loaded it
   * @throws ModuleLoadException
   */
  public Pair<ParsedModule, Boolean> loadIfNeeded(Context context,
                                             LocatedModule module)
             throws ModuleLoadException {
    boolean didLoad;
    ParsedModule parsed;
    if (loadedModuleMap.containsKey(module.canonicalName)) {
      didLoad = false;
      parsed = loadedModuleMap.get(module.canonicalName);
    } else {
      didLoad = true;
      // Load the file
      try {
        parsed = ParsedModule.parse(module.canonicalName, module.filePath,
                                    module.preprocessed);
      } catch (IOException e) {
        throw new ModuleLoadException(context, module.filePath, e);
      }
      loadedModuleMap.put(module.canonicalName, parsed);
      loadedModules.add(module);
    }
    return Pair.create(parsed, didLoad);
  }

  public ParsedModule currentModule() {
    return moduleStack.peek();
  }

  /**
   * Mark that we've entered the module
   * @param module
   * @param compiling true if we're compiling the module
   */
  public void enterModule(LocatedModule module, ParsedModule parsed) {
    moduleStack.push(parsed);
  }

  /**
   *
   */
  public void exitModule() {
    moduleStack.pop();
  }

  public LineMapping currLineMap() {
    assert(moduleStack.size() > 0);
    return moduleStack.peek().lineMapping;
  }

  private static String moduleCanonicalName(List<String> modulePath) {
    String canonicalName = "";
    boolean first = true;
    for (String component: modulePath) {
      if (first) {
        first = false;
      } else {
        canonicalName += ".";
      }
      canonicalName += component;
    }
    return canonicalName;
  }

  private static String locateModule(Context context, String moduleName,
                              List<String> modulePath) throws ModuleLoadException {
    for (String searchDir: Settings.getModulePath()) {
      if (searchDir.length() == 0) {
        continue;
      }
      String currDir = searchDir;
      // Find right subdirectory
      for (String subDir: modulePath.subList(0, modulePath.size() - 1)) {
        currDir = currDir + File.separator + subDir;
      }
      String fileName = modulePath.get(modulePath.size() - 1) + ".swift";
      String filePath = currDir + File.separator + fileName;

      if (new File(filePath).isFile()) {
        LogHelper.debug(context, "Resolved " + moduleName + " to " + filePath);
        return filePath;
      }
    }

    throw new ModuleLoadException(context, "Could not find module " + moduleName +
                  " in search path: " + Settings.getModulePath().toString());
  }



  public static class LocatedModule {
    public final String filePath;
    public final String canonicalName;
    public final boolean preprocessed;

    public LocatedModule(String filePath, String canonicalName,
      boolean preprocessed) {
      this.filePath = filePath;
      this.canonicalName = canonicalName;
      this.preprocessed = preprocessed;
    }

    /**
     * Create a module based on path.  This locates the module based on
     * the name within the module search path
     * @param context
     * @param modulePath
     * @return
     * @throws ModuleLoadException
     */
    public static LocatedModule fromPath(Context context, List<String> modulePath,
        boolean preprocessed) throws ModuleLoadException {
      String canonicalName = moduleCanonicalName(modulePath);
      String filePath = locateModule(context, canonicalName, modulePath);
      return new LocatedModule(filePath, canonicalName, preprocessed);
    }

    /**
     * Extract a path from an AST with a module name (e.g. in an import
     * statement), then locate the module.
     * @param context
     * @param moduleID
     * @return
     * @throws InvalidSyntaxException
     * @throws ModuleLoadException
     */
    public static LocatedModule fromModuleNameAST(Context context,
        SwiftAST moduleID, boolean preprocessed)
            throws InvalidSyntaxException, ModuleLoadException {
      List<String> modulePath;
      if (moduleID.getType() == ExMParser.STRING) {
        // Forms:
        //   module      => ./module.swift
        //   pkg/module  => pkg/module.swift
        // Implicit .swift extension added.  Relative to module search path
        String path = Literals.extractLiteralString(context, moduleID);
        modulePath = new ArrayList<String>();
        for (String elem: path.split("/+")) {
          modulePath.add(elem);
        }
      } else {
        assert(moduleID.getType() == ExMParser.IMPORT_PATH);
        // Forms:
        // pkg        => ./module.swift
        // pkg.module => pkg/module.swift
        modulePath = new ArrayList<String>();
        for (SwiftAST idT: moduleID.children()) {
          assert(idT.getType() == ExMParser.ID);
          modulePath.add(idT.getText());
        }
      }
      return fromPath(context, modulePath, preprocessed);
    }

  }

  public boolean needToCompileTopLevel(ParsedModule module) {
    return startedTopLevelCompile.add(module);
  }
}
