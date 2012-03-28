package exm.parser.ic.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exm.ast.Variable;
import exm.parser.ic.ICContinuations.Continuation;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.SwiftIC.Block;
import exm.parser.ic.SwiftIC.CompFunction;
import exm.parser.ic.SwiftIC.Program;

public class Flattener {

    public static void flattenNestedBlocks(Block block) {
    
    
    List<Continuation> originalContinuations = 
          new ArrayList<Continuation>(block.getContinuations());
    // Stick any nested blocks instructions into the main thing
    for (Continuation c: originalContinuations) {
      switch (c.getType()) {
      case NESTED_BLOCK:
        assert(c.getBlocks().size() == 1);
        Block inner = c.getBlocks().get(0);
        flattenNestedBlocks(inner);
        c.inlineInto(block, inner);
        break;
      default:
        // Recursively flatten any blocks inside the continuation
        for (Block b: c.getBlocks()) {
          flattenNestedBlocks(b);
        }
      }
      
    }
  }

  /**
   * Remove all nested blocks from program
   * Precondition: all variable names in composites should be unique
   * @param in
   * @return
   */
  public static Program flattenNestedBlocks(Program in) {
    for (CompFunction f: in.getComposites()) {
      flattenNestedBlocks(f.getMainblock());
    }
    return in; 
  }

  /**
   * Make all names in block unique
   * 
   * @param in
   * @param usedNames Names already used
   * @return
   */
  private static void makeVarNamesUnique(Block in, Set<String> usedNames) {
    HashMap<String, Oparg> renames = new HashMap<String, Oparg>(); 
    for (Variable v: in.getVariables()) {
      if (usedNames.contains(v.getName())) {
        int counter = 1;
        String newName; 
        // try x_1 x_2 x_3, etc until we find something
        do {
          newName = v.getName() + "_" + counter;
          counter++;
        } while(usedNames.contains(newName));
        renames.put(v.getName(), 
            Oparg.createVar(new Variable(v.getType(), newName, 
                            v.getStorage(), v.getDefType())));
        usedNames.add(newName);
      } else {
        usedNames.add(v.getName());
      }
    }
    
    // Rename variables in Block (and nested blocks) according to map
    in.renameVars(renames, false);
    
    // Recurse through nested blocks, making sure that all used variable
    // names are added to the usedNames
    for (Continuation c: in.getContinuations()) {
      for (Block b: c.getBlocks()) {
        makeVarNamesUnique(b, usedNames);
      }
    }
  }

  public static void makeVarNamesUnique(CompFunction in) {
    Set<String> usedNames = new HashSet<String>();
    for (Variable v: in.getInputList()) {
      usedNames.add(v.getName());
    }
    for (Variable v: in.getOutputList()) {
      usedNames.add(v.getName());
    }
    
    makeVarNamesUnique(in.getMainblock(), usedNames);
  }

  /**
   * Make all of variable names in composite functions completely
   * unique within the function 
   * @param in
   */
  public static void makeVarNamesUnique(Program in) {
    for (CompFunction f: in.getComposites()) {
      makeVarNamesUnique(f);
    }
  }

}
