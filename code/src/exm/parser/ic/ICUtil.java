package exm.parser.ic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import exm.ast.Variable;
import exm.parser.ic.ICContinuations.Continuation;
import exm.parser.ic.ICInstructions.Instruction;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.ICInstructions.OpargType;
import exm.parser.ic.SwiftIC.Block;

public class ICUtil {

  public static final String indent = "  ";
  
  /** Print a formal argument list, e.g. "(int a, int b, int c)" */
  public static void prettyPrintFormalArgs(StringBuilder sb,
                                                  List<Variable> args) {
    boolean first = true;
    sb.append("(");
    for (Variable a: args) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(a.getType().typeName() + " " + a.getName());
      first = false;
    }
    sb.append(")");
  }

  public static void prettyPrintVarInfo(StringBuilder sb,
          List<Variable> usedVariables, List<Variable> containersToBeRegistered) {
    if (usedVariables.size() > 0 ) {
      sb.append("#passin[");
      prettyPrintVarList(sb, usedVariables);
      sb.append("] ");
    }

    if (containersToBeRegistered.size() > 0) {
      sb.append("#keepopen[");
      prettyPrintVarList(sb, containersToBeRegistered);
      sb.append("]");
    }
  }

  /**
   * print a comma separated list of var names to sb
   * @param sb
   * @param vars
   */
  public static void prettyPrintVarList(StringBuilder sb, 
                Collection<Variable> vars) {
    boolean first = true;
    for (Variable v: vars) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(v.getName());
    }
  }


  /** replace matching variables in-place in list */
  public static void replaceVarsInList(Map<String, Variable> replacements,
      List<Variable> vars) {
    for (int i = 0; i < vars.size(); i++) {
      String varName = vars.get(i).getName();
      if (replacements.containsKey(varName)) {
        vars.set(i, replacements.get(varName));
      }
    }
  }

  /**
   * Replace variables by name in list
   * Remove variables with duplicate names
   * @param replacements
   * @param vars
   */
  public static void replaceVarsInList2(Map<String, Oparg> replacements,
      List<Variable> vars, boolean removeDupes) {
    // Remove new duplicates
    ArrayList<String> alreadySeen = null;
    if (removeDupes) {
      alreadySeen = new ArrayList<String>(vars.size());
    }
    
    int n = vars.size();
    for (int i = 0; i < n; i++) {
      String varName = vars.get(i).getName();
      if (replacements.containsKey(varName)) {
        Oparg oa = replacements.get(varName);
        if (oa.getType() == OpargType.VAR) {
          if (removeDupes && 
                  alreadySeen.contains(oa.getVariable().getName())) {
            vars.remove(i); i--; n--;
          } else {
            vars.set(i, oa.getVariable());
            if (removeDupes) {
              alreadySeen.add(oa.getVariable().getName());
            }
          }
        }
      } else {
        if (removeDupes) {
          if (alreadySeen.contains(varName)) {
            vars.remove(i); i--; n--;
          } else {
            alreadySeen.add(varName);
          }
        }
      }
    }
  }
  
  public static void removeDuplicates(List<Variable> varList) {
    ListIterator<Variable> it = varList.listIterator();
    HashSet<String> alreadySeen = new HashSet<String>();
    while (it.hasNext()) {
      Variable v = it.next();
      if (alreadySeen.contains(v.getName())) {
        it.remove();
      } else {
        alreadySeen.add(v.getName());
      }
    }
  }

  public static void replaceOpargsInList(Map<String, Variable> renames,
      List<Oparg> args) {
    for (int i = 0; i < args.size(); i++) {
      Oparg oa = args.get(i);
      if (oa.getType() == OpargType.VAR) {
        String oldName = oa.getVariable().getName();
        if (renames.containsKey(oldName)) {
          oa.replaceVariable(renames.get(oldName));
        }
      }
    }
  }
  
  public static void replaceOpargsInList2(Map<String, Oparg> renames,
      List<Oparg> args) {
    for (int i = 0; i < args.size(); i++) {
      Oparg oa = args.get(i);
      if (oa.getType() == OpargType.VAR) {
        String oldName = oa.getVariable().getName();
        if (renames.containsKey(oldName)) {
          args.set(i, renames.get(oldName));
        }
      }
    }
  }
  
  public static void removeVarInList(List<Variable> varList,
        String toRemove) {
    int n = varList.size();
    for (int i = 0; i < n; i ++) {
      if (varList.get(i).getName().equals(toRemove)) {
        varList.remove(i);
        i--; n--;
      }
    }
  }
  
  public static void removeVarsInList(List<Variable> varList,
      Set<String> removeVars) {
      int n = varList.size();
      
      for (int i = 0; i < n; i++) {
        if (removeVars.contains(varList.get(i).getName())) {
          varList.remove(i);
          n--;
          i--;
        }
      }
   }

  public static LinkedList<Instruction> cloneInstructions(
      List<Instruction> instructions) {
    LinkedList<Instruction> output = new LinkedList<Instruction>();
    for (Instruction i : instructions) {
      output.add(i.clone());
    }
    return output;
  }

  public static ArrayList<Continuation> cloneContinuations(
      List<Continuation> conts) {
    ArrayList<Continuation> newContinuations = 
                        new ArrayList<Continuation>(conts.size());
    for (Continuation old: conts) {
      newContinuations.add(old.clone());
    }
    return newContinuations;
  }

  public static ArrayList<Block> cloneBlocks(List<Block> blocks) {
    ArrayList<Block> newBlocks = new ArrayList<Block>(blocks.size());
    for (Block old: blocks) {
      newBlocks.add(old.clone());
    }
    return newBlocks;
  }
  
  /** 
   * Replace the current instruction with the provided sequence
   * After this is done, next() will return the instruction after
   * the inserted sequence
   */
  public static void replaceInsts(ListIterator<Instruction> it, 
              List<Instruction> replacements) {

    if (replacements.size() == 1) {
      it.set(replacements.get(0));
    } else if (replacements.size() == 0) {
      it.remove();
    } else {
      it.set(replacements.get(0));
      List<Instruction> rest = replacements.subList(1, replacements.size());
      for (Instruction newInst: rest) {
        it.add(newInst);
      }
    }
  }
  public static void rewindIterator(
      @SuppressWarnings("rawtypes") ListIterator it, int n) {
    for (int i = 0; i < n; i++) {
      it.previous();
    }
  }
}
