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
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;

/**
 * This module contains logic to create and initialise variables, in order
 * to ensure that variable are consistently created and initialised in front end.
 * This is potentially a problem because we store variable state in both the
 * context and in the backend.
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

  /**
   * Convenience shortcut createTmp: creates a local temporary + storage
   * which isn't stored in stack
   * @param context
   * @param type
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Variable createTmp(Context context, SwiftType type) 
      throws UserException, UndefinedTypeException {
    return createTmp(context, type, false, false);
  }
  
  /**
   * Creates a new tmp value, entering it in the provided context
   * and calling the backend to initialise it 
   * @param context
   * @param type
   * @param storeInStack if the variable should be stored in the stack
   * @param isAlias if the variable is just going to be an alias for another
   *      variable (i.e. no storage should be declared)
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Variable createTmp(Context context, SwiftType type,
      boolean storeInStack, boolean isAlias) throws UserException,
      UndefinedTypeException {
    if (storeInStack && isAlias) {
      throw new STCRuntimeError("Cannot create variable which is both alias" +
      		" and on stack");
    }
    Variable tmp;
    if ((!storeInStack) && isAlias) {
      tmp = context.createAliasVariable(type);
    } else {
      tmp = context.createTmpVar(type, storeInStack);
    }

    initialiseVariable(context, tmp);
    return tmp;
  }

  public Variable createValueOfVar(Context context, Variable future) 
      throws UserException {
    return createValueOfVar(context, future, true);
  }
  /**
   * Create a value variable which ahs the type which is the value equivalent
   * to a scalar future
   * @param context
   * @param future
   * @param initialise false if the variable doesn't need to be initialised (e.g.
   *    if it is initialised by a surrounding construct)
   * @return
   * @throws UserException
   */
  public Variable createValueOfVar(Context context, Variable future,
        boolean initialise)
                                                  throws UserException {
    assert(Types.isScalarFuture(future.getType()));
    SwiftType valType = Types.derefResultType(future.getType());
    Variable val = context.createLocalValueVariable(valType, future.getName());
    if (initialise) {
      initialiseVariable(context, val);
    }
    return val;
  }
  
  
  /**
   * Create a value variable and retrieve value of future into it
   * @param context
   * @param type
   * @param future
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  public Variable fetchValueOf(Context context, Variable future) 
      throws UserException, UndefinedTypeException, DoubleDefineException {
    assert(Types.isScalarFuture(future.getType()));
    SwiftType futureType = future.getType();
    Variable val = createValueOfVar(context, future);
    switch (futureType.getPrimitiveType()) {
    case BOOLEAN:
      backend.retrieveBool(val, future);
      break;
    case INTEGER:
      backend.retrieveInt(val, future);
      break;
    case STRING:
      backend.retrieveString(val, future);
      break;
    case FLOAT:
      backend.retrieveFloat(val, future);
      break;
    default:
      throw new STCRuntimeError("Don't know how to retrieve value of "
          + " type " + futureType.typeName() + " for variable " 
          + future.getName());
    }
    
    return val;
  }
  
}
