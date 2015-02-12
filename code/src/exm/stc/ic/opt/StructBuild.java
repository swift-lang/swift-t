package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.StackLite;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.Opcode;
import exm.stc.ic.tree.TurbineOp;

/**
 * TODO: combine with array build?
 * TODO: store array references if assigned in whole?
 */
public class StructBuild extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Struct build";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_ARRAY_BUILD;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    logger.trace("Struct build in " + f.id());
    structBuildRec(logger, f.mainBlock());
  }

  private void structBuildRec(Logger logger, Block block) {
    // Track all assigned struct paths
    MultiMap<Var, List<String>> assignedPaths = new MultiMap<Var, List<String>>();

    // Find all struct assign statements in block
    for (Statement stmt: block.getStatements()) {
      if (stmt.type() == StatementType.INSTRUCTION) {
        Instruction inst = stmt.instruction();
        if (inst.op == Opcode.STRUCT_STORE_SUB) {
          Var struct = inst.getOutput(0);
          List<Arg> inputs = inst.getInputs();
          List<Arg> fields = inputs.subList(1, inputs.size());
          assignedPaths.put(struct, Arg.extractStrings(fields));
          System.err.println(inst);
        }
      }
    }

    // Check if all fields were assigned
    for (Var candidate: assignedPaths.keySet()) {
      StructType candidateType = (StructType)candidate.type().getImplType();
      Set<List<String>> expectedPaths = allAssignablePaths(candidateType);
      List<List<String>> assigned = assignedPaths.get(candidate);

      logger.trace("Check candidate " + candidate.name() + "\n" +
                   "expected: " + expectedPaths + "\n" +
                   "assigned: " + assigned);

      for (List<String> path: assigned) {
        Type fieldType;
        try {
          fieldType = candidateType.fieldTypeByPath(path);
        } catch (TypeMismatchException e) {
          throw new STCRuntimeError(e.getMessage());
        }

        Set<List<String>> assignedSubPaths;
        if (Types.isStruct(fieldType)) {
          // Handle case where we assign a substruct
          StructType structFieldType = (StructType)fieldType.getImplType();
          assignedSubPaths = allAssignablePaths(structFieldType, path);
        } else {
          assignedSubPaths = Collections.singleton(path);
        }

        for (List<String> assignedPath: assignedSubPaths) {
          boolean found = expectedPaths.remove(assignedPath);
          if (!found) {
            logger.warn("Invalid or double-assigned struct field: " +
                         candidate.name() + "." + assignedPath);
          }
        }
      }
      if (expectedPaths.isEmpty()) {
        doStructBuildTransform(logger, block, candidate, assigned.size());
      } else if (logger.isTraceEnabled()) {
        logger.trace("Fields not assigned: " + expectedPaths);
      }
    }

    for (Continuation cont: block.allComplexStatements()) {
      for (Block cb: cont.getBlocks()) {
        structBuildRec(logger, cb);
      }
    }
  }

  /**
   * Work out all of the paths that need to be assigned for the struct
   * @param candidate
   * @return
   */
  private Set<List<String>> allAssignablePaths(StructType type) {
    return allAssignablePaths(type, Collections.<String>emptyList());
  }

  private Set<List<String>> allAssignablePaths(StructType type,
                                               List<String> prefix) {
    Set<List<String>> paths = new HashSet<List<String>>();
    StackLite<String> prefixStack = new StackLite<String>();
    prefixStack.pushAll(prefix);
    addAssignablePaths(type, prefixStack, paths);
    return paths;
  }

  private void addAssignablePaths(StructType type, StackLite<String> prefix,
      Set<List<String>> paths) {
    for (StructField f: type.fields()) {
      prefix.push(f.name());
      if (Types.isStruct(f.type())) {
        addAssignablePaths((StructType)f.type().getImplType(), prefix,
                           paths);
      } else {
        // Must be assigned
        paths.add(new ArrayList<String>(prefix));
      }
      prefix.pop();
    }
  }

  /**
   * Replace struct stores with struct build
   * @param logger
   * @param block
   * @param candidate
   * @param fieldsToAssign number of fields assigned
   */
  private void
      doStructBuildTransform(Logger logger, Block block, Var candidate,
                             int fieldsToAssign) {
    logger.trace("Transforming " + candidate.name());
    int fieldsAssigned = 0;
    List<List<String>> fieldPaths = new ArrayList<List<String>>();
    List<Arg> fieldVals = new ArrayList<Arg>();

    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        Instruction inst = stmt.instruction();
        if (inst.op == Opcode.STRUCT_STORE_SUB) {
          Var struct = inst.getOutput(0);
          if (struct.equals(candidate)) {
            stmtIt.remove();
            fieldsAssigned++;

            List<Arg> inputs = inst.getInputs();
            fieldPaths.add(
                Arg.extractStrings(inputs.subList(1, inputs.size())));
            fieldVals.add(inputs.get(0));
          }
        }
      }
      if (fieldsAssigned == fieldsToAssign) {
        // Assign to local struct then store
        Var localStruct = OptUtil.createDerefTmp(block, candidate);
        stmtIt.add(TurbineOp.structLocalBuild(localStruct, fieldPaths, fieldVals));
        stmtIt.add(TurbineOp.assignStruct(candidate, localStruct.asArg()));
        return;
      }
    }

    // Should not fall out of loop
    assert(false);
  }

}
