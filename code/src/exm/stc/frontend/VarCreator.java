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

import java.util.List;
import java.util.Stack;

import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.STCMiddleEnd;

/**
 * This module contains logic to create and initialise variables, in order
 * to ensure that variable are consistently created and initialised in front end.
 * This is potentially a problem because we store variable state in both the
 * context and in the backend.
 *
 */
public class VarCreator {
  private STCMiddleEnd backend;

  public VarCreator(STCMiddleEnd backend) {
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
  public Var createVariable(Context context, Type type, String name,
      VarStorage storage, DefType defType, Var mapping)
                                                throws UserException {

    if (mapping != null && (!Types.isMappable(type))) {
      throw new UserException(context, "Variable " + name + " of type "
          + type.toString() + " cannot be mapped to " + mapping);
    }
    Var v;

    try {
      v = context.declareVariable(type, name, storage, defType, mapping);
    } catch (DoubleDefineException e) {
      throw new DoubleDefineException(context, e.getMessage());
    }
    initialiseVariable(context, v);
    return v;
  }
  
  public Var createVariable(Context context, Var newVar) throws UserException {
    return createVariable(context, newVar.type(), newVar.name(),
            newVar.storage(), newVar.defType(), newVar.mapping());
  }

  public void initialiseVariable(Context context, Var v)
      throws UndefinedTypeException, DoubleDefineException {
    if (!Types.isStruct(v.type())) {
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
  public void declare(Var var) throws UndefinedTypeException {
    backend.declare(var.type(), var.name(), 
        var.storage(), var.defType(), var.mapping());
  }

  private void initialiseStruct(Context context, Var rootStruct,
              Var structToInit, Stack<String> path)
      throws UndefinedTypeException, DoubleDefineException {
    assert(Types.isStruct(structToInit.type()));
    
    declare(structToInit);
    
    if (structToInit.storage() == VarStorage.ALIAS) {
      // Skip recursive initialisation if its just an alias
      return;
    } else {
      StructType type = (StructType)structToInit.type();
  
      for (StructField f: type.getFields()) {
        path.push(f.getName());
  
        Var tmp = context.createStructFieldTmp(
            rootStruct, f.getType(), path, VarStorage.TEMP);
  
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
  public Var createTmp(Context context, Type type) 
      throws UserException, UndefinedTypeException {
    assert(context != null);
    return createTmp(context, type, false, false);
  }
  
  /**
   * Shortcut to create tmp alias vars
   * @param context
   * @param type
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Var createTmpAlias(Context context, Type type) 
      throws UserException, UndefinedTypeException {
    return createTmp(context, type, false, true);
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
  public Var createTmp(Context context, Type type,
      boolean storeInStack, boolean isAlias) throws UserException,
      UndefinedTypeException {
    assert(context != null);
    if (storeInStack && isAlias) {
      throw new STCRuntimeError("Cannot create variable which is both alias" +
              " and on stack");
    }
    Var tmp;
    if ((!storeInStack) && isAlias) {
      tmp = context.createAliasVariable(type);
    } else {
      tmp = context.createTmpVar(type, storeInStack);
    }

    initialiseVariable(context, tmp);
    return tmp;
  }
  
  public Var createTmpLocalVal(Context context, Type type) 
        throws UserException {
    assert(Types.isScalarValue(type));
    Var val = context.createLocalValueVariable(type);
    declare(val);
    return val;
  }
  
  public Var createStructFieldTmp(Context context, Var rootStruct, 
                  Type memType, List<String> fieldPath,
                  VarStorage storage) throws UndefinedTypeException {
    Var tmp = context.createStructFieldTmp(rootStruct, memType,
          fieldPath, storage);
    declare(tmp);
    return tmp;
  }

  public Var createValueOfVar(Context context, Var future) 
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
  public Var createValueOfVar(Context context, Var future,
        boolean initialise)
                                                  throws UserException {
    assert(Types.isScalarFuture(future.type()));
    Type valType = Types.derefResultType(future.type());
    Var val = context.createLocalValueVariable(valType, future.name());
    if (initialise) {
      initialiseVariable(context, val);
    }
    return val;
  }
  
  /**
   * Shortcut to create filename of
   * @param context
   * @return
   */
  public Var createFilenameAlias(Context context, Var fileVar)
      throws UserException, UndefinedTypeException {
    assert(Types.isFile(fileVar.type()));
    Var filename = context.createFilenameAliasVariable(
        fileVar.name());
    initialiseVariable(context, filename);
    return filename;
  }
  
  
  /**
   * Create a value variable and retrieve value of future into it
   * @param context
   * @param future
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  public Var fetchValueOf(Context context, Var future) 
      throws UserException, UndefinedTypeException, DoubleDefineException {
    assert(Types.isScalarFuture(future.type()));
    Type futureType = future.type();
    Var val = createValueOfVar(context, future);
    switch (futureType.primType()) {
    case BOOL:
      backend.retrieveBool(val, future);
      break;
    case INT:
      backend.retrieveInt(val, future);
      break;
    case STRING:
      backend.retrieveString(val, future);
      break;
    case FLOAT:
      backend.retrieveFloat(val, future);
      break;
    case BLOB:
      backend.retrieveBlob(val, future);
      break;
    case VOID:
      backend.retrieveVoid(val, future);
      break;
    case FILE:
      backend.retrieveFile(val, future);
      break;
    default:
      throw new STCRuntimeError("Don't know how to retrieve value of "
          + " type " + futureType.typeName() + " for variable " 
          + future.name());
    }
    
    return val;
  }
  
}
