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
package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.componentaliases.Component;
import exm.stc.ic.componentaliases.ComponentAlias;
import exm.stc.ic.componentaliases.ComponentGraph;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalVars;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.Opcode;

public class DeadCodeEliminator implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Dead code elimination";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_DEAD_CODE_ELIM;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
    for (Function f: program.functions()) {
      eliminate(logger, f, program.globalVars());
    }
  }

  /**
   * Eliminates dead code in the current block and child blocks. Since this is a
   * data flow language, the easiest way to do this is to find variables which
   * aren't needed, eliminate those and the instructions which write to them,
   * and then do that repeatedly until we don't have anything more to eliminate.
   *
   * We avoid eliminating any instructions with side-effects, and anything that
   * contributes to the return value of a function. We currently assume that all
   * non-builtin functions have side effects, as well as any builtins operations
   * that are not specifically marked as side-effect free.
   *
   * @param logger
   * @param f
   * @param globalVars
   */
  public static void eliminate(Logger logger, Function f, GlobalVars globalVars) {
    boolean converged = false;
    boolean changed = false;
    while (!converged) {
      /*
       * Dead variable elimination can allow dead code blocks to be removed,
       * which can allow more variables to be removed.  So we should just
       * iterate until no more changes were made
       */
      boolean changeThisIter = eliminateIter(logger, f, globalVars);
      converged = !changeThisIter;
      changed = changed || changeThisIter;
    }

    if (changed) {
      inlineContinuations(logger, f.mainBlock());
    }
  }

  /**
   *
   * @param logger
   * @param f
   * @param globalVars
   * @return true if changes made
   */
  private static boolean eliminateIter(Logger logger, Function f,
                                        GlobalVars globalVars) {
    /* All vars defined in function blocks that could possibly be eliminated */
    HashSet<Var> removeCandidates = new HashSet<Var>();

    /* Set of vars that are definitely required */
    HashSet<Var> needed = new HashSet<Var>();
    needed.addAll(globalVars.variables());

    /* List of vars that were written.  Need to ensure that all variables
     * that are keys in writeEffect are tracked. */
    List<Component> modifiedComponents = new ArrayList<Component>();
    /*
     * Graph of dependencies from vars to other vars. If edge exists v1 -> v2
     * this means that if v1 is required, then v2 is required
     */
    MultiMap<Var, Var> dependencyGraph = new MultiMap<Var, Var>();

    /* Track components so that we know if a write from A may flow to B*/
    ComponentGraph components = new ComponentGraph();

    walkFunction(logger, f, removeCandidates, needed, dependencyGraph,
                 modifiedComponents, components);

    if (logger.isTraceEnabled()) {
      logger.trace("Dead code elimination in function " + f.id() + "\n" +
                   "removal candidates: " + removeCandidates + "\n" +
                   "definitely needed: "+ needed + "\n" +
                   "dependencies: \n" + printDepGraph(dependencyGraph, 4) +
                   "modifiedComponents: " + modifiedComponents + "\n" +
                   "components: \n" + components);
    }

    /*
     * Add in component info.
     * Take into account that we might modify value of containing
     * structure, e.g. array
     */
    for (Component written: modifiedComponents) {
      Set<Var> potentialAliases = components.findPotentialAliases(written);

      if (logger.isTraceEnabled()) {
        logger.trace("Modified var " + written + " potential aliases: " +
                      potentialAliases);
      }
      for (Var maybeAffected: potentialAliases) {
        if (logger.isTraceEnabled()) {
          logger.trace("Add transitive dep " + maybeAffected +
                        " => " + written);
        }
        // Need to keep var that we wrote the affected var through
        dependencyGraph.put(maybeAffected, written.var);
      }
    }

    if (logger.isTraceEnabled())
      logger.trace("dependencies after component updates: \n" +
                   printDepGraph(dependencyGraph, 4));
    /*
     * Expand set of needed based on dependency graph
     */
    StackLite<Var> workStack = new StackLite<Var>();
    workStack.addAll(needed);

    while (!workStack.isEmpty()) {
      Var neededVar = workStack.pop();
      // This loop converges as dependencyGraph is taken apart
      List<Var> deps = dependencyGraph.remove(neededVar);
      if (deps != null) {
        needed.addAll(deps);
        workStack.addAll(deps);
      }
    }

    removeCandidates.removeAll(needed);

    if (logger.isDebugEnabled()) {
      logger.debug("Final variables to be eliminated: " + removeCandidates);
    }
    if (removeCandidates.isEmpty()) {
      return false;
    } else {
      f.mainBlock().removeVars(removeCandidates);
      return true;
    }
  }

  /**
   * Collect information for dead code elimination
   *
   * @param logger
   * @param f
   * @param removeCandidates list of vars declared in function that
   *                           could be removed
   * @param needed
   * @param dependencyGraph
   * @param modifiedVars
   * @param components
   */
  private static void walkFunction(Logger logger, Function f,
      HashSet<Var> removeCandidates, HashSet<Var> needed,
      MultiMap<Var, Var> dependencyGraph, List<Component> modifiedComponents,
      ComponentGraph components) {
    StackLite<Block> workStack = new StackLite<Block>();
    workStack.push(f.mainBlock());

    needed.addAll(f.getOutputList());

    while (!workStack.isEmpty()) {
      Block block = workStack.pop();

      walkBlockVars(block, removeCandidates, dependencyGraph);

      walkInstructions(logger, block, needed, dependencyGraph,
                       modifiedComponents, components);

      Iterator<Continuation> it = block.allComplexStatements().iterator();
      while (it.hasNext()) {
        Continuation c = it.next();
        if (c.isNoop()) {
          it.remove();
        } else {
          // Add vars for continuation
          needed.addAll(c.requiredVars(true));

          for (Block inner: c.getBlocks()) {
            workStack.push(inner);
          }
        }
      }
    }
  }

  private static void walkInstructions(Logger logger,
      Block block, HashSet<Var> needed, MultiMap<Var, Var> dependencyGraph,
      List<Component> modifiedComponents, ComponentGraph components) {
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        walkInstruction(logger, stmt.instruction(), needed, dependencyGraph,
                        modifiedComponents, components);
      } else if (stmt.type() == StatementType.CONDITIONAL) {
        if (stmt.conditional().isNoop()) {
          it.remove();
        }
      }
    }
  }

  private static void walkInstruction(Logger logger, Instruction inst,
      HashSet<Var> needed, MultiMap<Var, Var> dependencyGraph,
      List<Component> modifiedComponents, ComponentGraph components) {
    // If it has side-effects, need all inputs and outputs
    if (inst.hasSideEffects()) {
      needed.addAll(inst.getOutputs());
      for (Arg input: inst.getInputs()) {
        if (input.isVar()) {
          needed.add(input.getVar());
        }
      }
    } else {
      // Add edges to dependency graph
      List<Var> outputs = inst.getOutputs();
      List<Var> modOutputs = inst.getModifiedOutputs();
      List<Var> readOutputs = inst.getReadOutputs();
      List<Arg> inputs = inst.getInputs();

      // First, if multiple modified outputs, need to remove all at once
      if (modOutputs.size() > 1) {
        // Connect mod outputs in ring so that they are
        // strongly connected component
        for (int i = 0; i < modOutputs.size(); i++) {
          int j = (i + 1) % modOutputs.size();
          dependencyGraph.put(modOutputs.get(i), modOutputs.get(j));
        }
      }
      if (modOutputs.size() > 0) {
        // Second, modified output depends on all inputs and read outputs.
        // Just use one output if multiple
        Var out = modOutputs.get(0);
        for (Arg in: inputs) {
          if (in.isVar()) {
            addOutputDep(logger, inst, dependencyGraph, components, out,
                         in.getVar());
          }
        }

        for (Var readOut: readOutputs) {
          addOutputDep(logger, inst, dependencyGraph, components, out,
                       readOut);
        }
      }
      // Writing mapped var can have side-effect, unless we're storing a
      // mapped value var that was written
      for (Var output: outputs) {
        if (output.isMapped() != Ternary.FALSE &&
            inst.op != Opcode.STORE_FILE) {
          needed.add(output);
        }
      }

      // Update any components that were modified
      List<Component> modComponents = inst.getModifiedComponents();
      if (modComponents != null)
      {
        for (Component mod: modComponents) {
          addModComponent(inst, modifiedComponents, mod);
        }
      }
      else
      {
        for (Var mod: modOutputs) {
          addModComponent(inst, modifiedComponents,
                        new Component(mod, Arg.NONE));
        }
      }

      // Update structural information
      for (ComponentAlias componentAlias: inst.getComponentAliases()) {
        components.addPotentialComponent(componentAlias);
      }
    }
  }

  private static void addModComponent(Instruction inst,
      List<Component> modifiedComponents, Component component) {
    if (component.key.isEmpty()) {
      boolean modInit = inst.isInitialized(component.var);
      if (!modInit) {
        modifiedComponents.add(component);
      }
    }
    else
    {
      modifiedComponents.add(component);
    }
  }

  private static void addOutputDep(Logger logger,
      Instruction inst, MultiMap<Var, Var> dependencyGraph,
      ComponentGraph components, Var out, Var in) {
    if (logger.isTraceEnabled())
      logger.trace("Add dep " + out + " => " + in + " for inst " + inst);
    dependencyGraph.put(out, in);
  }

  private static void walkBlockVars(Block block,
      HashSet<Var> removeCandidates, MultiMap<Var, Var> dependencyGraph) {
    for (Var v: block.variables()) {
      if (!v.storage().isGlobal()) {
        removeCandidates.add(v);
      }
    }
  }

  private static String printDepGraph(MultiMap<Var, Var> dependencyGraph,
                                      int indent) {
    List<Var> keys = new ArrayList<Var>(dependencyGraph.keySet());
    Collections.sort(keys);
    StringBuilder sb = new StringBuilder();
    for (Var key: keys) {
      for (int i = 0; i < indent; i++) {
        sb.append(' ');
      }
      sb.append(key.name() + " => [");
      ICUtil.prettyPrintVarList(sb, dependencyGraph.get(key));
      sb.append("]\n");
    }

    return sb.toString();
  }

  /**
   * Inline continuations, e.g. waits with no arguments to cleanup
   * for any removed variables
   * @param logger
   * @param block
   */
  private static void inlineContinuations(Logger logger, Block block) {
    Set<Var> noClosedVars = Collections.emptySet();

    for (Statement stmt: block.getStatements()) {
      if (stmt.type() == StatementType.CONDITIONAL) {
        for (Block cb: stmt.conditional().getBlocks()) {
          inlineContinuations(logger, cb);
        }
      }
    }

    ListIterator<Continuation> contIt = block.continuationIterator();
    while (contIt.hasNext()) {
      Continuation c = contIt.next();
      Block toInline = null;
      toInline = c.tryInline(noClosedVars, noClosedVars, true);
      if (toInline != null) {
        // Remove old and then add new
        contIt.remove();
        block.insertInline(toInline, contIt, block.statementEndIterator());
        logger.trace("Inlined continuation " + c.getType());
      } else {
        for (Block cb: c.getBlocks()) {
          inlineContinuations(logger, cb);
        }
      }
    }
  }

}