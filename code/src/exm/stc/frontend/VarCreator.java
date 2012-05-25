package exm.stc.frontend;

import java.util.Stack;

import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Types.StructType;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Types.StructType.StructField;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.common.CompilerBackend;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;

/**
 * This module contains logic to create and initialise variables
 *
 */
public class VarCreator {
  private CompilerBackend backend;

  public VarCreator(CompilerBackend backend) {
    super();
    this.backend = backend;
  }

  /**
   * @param context
   * @param type
   * @param name
   * @param storage
   * @return
   * @throws UserException
   */
  public Variable createVariable(Context context, SwiftType type, String name,
      VariableStorage storage, DefType defType, Variable mapping)
                                                throws UserException {

    if (mapping != null && (!Types.isMappable(type))) {
      throw new UserException(context, "Variable " + name + " of type "
          + type.toString() + " cannot be mapped to " + mapping);
    }
    Variable v;

    try {
      v = context.declareVariable(type, name, storage, defType, mapping);
    } catch (DoubleDefineException e) {
      throw new DoubleDefineException(context, e.getMessage());
    }
    initialiseVariable(context, v);
    return v;
  }
  
  public void initialiseVariable(Context context, Variable v)
      throws UndefinedTypeException, DoubleDefineException {
    if (!Types.isStruct(v.getType())) {
      declare(v);
    } else {
      // Need to handle structs specially because they have lots of nested
      // variables created at declaration time
      initialiseStruct(context, v, v, new Stack<String>());
    }
  }

  /**
   * Convenience function to declare var in backend 
   * @param var
   * @throws UndefinedTypeException
   */
  public void declare(Variable var) throws UndefinedTypeException {
    backend.declare(var.getType(), var.getName(), 
        var.getStorage(), var.getDefType(), var.getMapping());
  }

  private void initialiseStruct(Context context, Variable rootStruct,
              Variable structToInit, Stack<String> path)
      throws UndefinedTypeException, DoubleDefineException {
    assert(Types.isStruct(structToInit.getType()));
    
    declare(structToInit);
    
    if (structToInit.getStorage() == VariableStorage.ALIAS) {
      // Skip recursive initialisation if its just an alias
      return;
    } else {
      StructType type = (StructType)structToInit.getType();
  
      for (StructField f: type.getFields()) {
        path.push(f.getName());
  
        Variable tmp = context.createStructFieldTmp(
            rootStruct, f.getType(), path, VariableStorage.TEMPORARY);
  
        if (Types.isStruct(f.getType())) {
          // Continue recursive structure initialisation,
          // while keeping track of the full path
          initialiseStruct(context, rootStruct, tmp, path);
        } else {
          initialiseVariable(context, tmp);
        }
        backend.structInsert(structToInit, f.getName(), tmp);
        path.pop();
      }
      backend.structClose(structToInit);
    }
  }

  public Variable createTmp(Context context, SwiftType type,
      boolean storeInStack, boolean useAlias) throws UserException,
      UndefinedTypeException {
    Variable tmp;
    if ((!storeInStack) && useAlias) {
      tmp = context.createAliasVariable(type);
    } else if (storeInStack) {
      tmp = context.createIntermediateVariable(type);
    } else {
      tmp = context.createLocalTmpVariable(type);
    }

    initialiseVariable(context, tmp);
    return tmp;
  }

  
}
