package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Types.FunctionType;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Types.FunctionType.InArgT;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.frontend.Context;

public class FunctionDecl {
  private final FunctionType ftype;
  private final ArrayList<String> inNames;
  private final ArrayList<String> outNames;
  
  
  private FunctionDecl(FunctionType ftype, ArrayList<String> inNames,
      ArrayList<String> outNames) {
    super();
    this.ftype = ftype;
    this.inNames = inNames;
    this.outNames = outNames;
  }

  public FunctionType getFunctionType() {
    return ftype;
  }


  public List<String> getInNames() {
    return Collections.unmodifiableList(inNames);
  }


  public List<String> getOutNames() {
    return Collections.unmodifiableList(outNames);
  }
  
  private static class ArgDecl {
    final String name;
    final InArgT type;
    final boolean varargs;
    private ArgDecl(String name, InArgT type, boolean varargs) {
      super();
      this.name = name;
      this.type = type;
      this.varargs = varargs;
    }
  }

  public static FunctionDecl fromAST(Context context, SwiftAST inArgTree, 
                        SwiftAST outArgTree) throws TypeMismatchException, UndefinedTypeException, InvalidSyntaxException {
    assert(inArgTree.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(outArgTree.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    ArrayList<String> inNames = new ArrayList<String>();
    ArrayList<InArgT> inArgTypes = new ArrayList<InArgT>();
    boolean varArgs = false;
    for (int i = 0; i < inArgTree.getChildCount(); i++) {
      ArgDecl argInfo = extractArgInfo(context, inArgTree.child(i));
      inNames.add(argInfo.name);
      inArgTypes.add(argInfo.type);
      if (argInfo.varargs) {
        if (i != inArgTree.getChildCount() - 1) {
          throw new TypeMismatchException(context, "variable argument marker "
              + "... must be in final position of input argument list");
        }
        varArgs = true;
      }
    }
    assert(inNames.size() == inArgTypes.size());

    ArrayList<String> outNames = new ArrayList<String>();
    ArrayList<SwiftType> outArgTypes = new ArrayList<SwiftType>();
    for (int i = 0; i < outArgTree.getChildCount(); i++) {
      ArgDecl argInfo = extractArgInfo(context, outArgTree.child(i));
      if (argInfo.varargs) {
        throw new TypeMismatchException(context, "cannot have variable" +
        		" argument specifier ... in output list");
      } else if (argInfo.type.getAltCount() != 1) {
        throw new TypeMismatchException(context, "Cannot have" +
        		" polymorphic function output type: " + argInfo.type.typeName());
      } else {
        outArgTypes.add(argInfo.type.getAlt(0));
        outNames.add(argInfo.name);
      }
    }
    assert(outNames.size() == outArgTypes.size());
    FunctionType ftype = new FunctionType(inArgTypes, outArgTypes, varArgs);
    return new FunctionDecl(ftype, inNames, outNames);
  }

  private static ArgDecl extractArgInfo(Context context, SwiftAST arg)
      throws UndefinedTypeException, InvalidSyntaxException {
    assert(arg.getType() == ExMParser.DECLARATION);
    assert(arg.getChildCount() == 2 || arg.getChildCount() == 3);
    
    ArgDecl argInfo;
    boolean thisVarArgs = false;
    if (arg.getChildCount() == 3) {
      assert(arg.child(2).getType() == ExMParser.VARARGS);
      thisVarArgs = true;
    }
    SwiftAST baseTypes = arg.child(0);
    SwiftAST restDecl = arg.child(1);
    assert(baseTypes.getType() == ExMParser.MULTI_TYPE);
    assert(baseTypes.getChildCount() >= 1); // Grammar should ensure this
    assert(restDecl.getType() == ExMParser.DECLARE_VARIABLE_REST);
    SwiftType alts[] = new SwiftType[baseTypes.getChildCount()];
    String varname = null;
    for (int i = 0; i < baseTypes.getChildCount(); i++) {
      SwiftAST typeAlt = baseTypes.child(i);
      assert(typeAlt.getType() == ExMParser.ID);
      String typeName = typeAlt.getText();
      SwiftType baseType = context.lookupType(typeName);
      if (baseType == null) {
        throw new UndefinedTypeException(context, typeName);
      }
      Variable v = fromFormalArgTree(context, baseType, 
                                                    restDecl, DefType.INARG);
      varname = v.getName();
      alts[i] = v.getType();
    }
    InArgT argType1 = new InArgT(alts);
    
    InArgT argType = argType1;
    argInfo = new ArgDecl(varname, argType, thisVarArgs);
    return argInfo;
  }
  
  
  /**
    * Take a DECLARE_VARIABLE_REST subtree of the AST and return the appropriate declared
    * variable.  Doesn't check to see if variable already defined
    * @param errorContext the current context, for info to add to error message
    * @param baseType the type preceding the declaration 
    * @param tree a parse tree with the root a DECLARE_MULTI or DECLARE_SINGLE 
    *                                                               subtree
    * @return
    * @throws UndefinedTypeException
   * @throws InvalidSyntaxException 
    */
    public static Variable fromFormalArgTree(
        Context context, SwiftType baseType, SwiftAST tree, DefType deftype)
    throws UndefinedTypeException, InvalidSyntaxException
    {
      assert(tree.getType() == ExMParser.DECLARE_VARIABLE_REST);
      assert(tree.getChildCount() >= 1);
      SwiftAST nameTree = tree.child(0);
      assert(nameTree.getType() == ExMParser.ID);
      String varName = nameTree.getText();
      
      SwiftType varType = baseType;
      for (int i = 1; i < tree.getChildCount(); i++) {
        SwiftAST subtree = tree.child(i);
        if (subtree.getType() == ExMParser.ARRAY) {
          varType = new Types.ArrayType(varType);
        } else if (subtree.getType() == ExMParser.MAPPING) {
          throw new InvalidSyntaxException(context, "Cannot map function argument");
        } else {
          throw new STCRuntimeError("Unexpected token in variable " +
              "declaration: " + ExMParser.tokenNames[subtree.getType()]);
        }
      }
      return new Variable(varType, varName, VariableStorage.STACK, deftype, 
                                                                null);
    }

  public List<Variable> getInVars() {
    ArrayList<Variable> inVars = new ArrayList<Variable>(inNames.size());
    for (int i = 0; i < inNames.size(); i++) {
      InArgT it = ftype.getInputs().get(i);
      if (it.getAlternatives().length != 1) {
        throw new STCRuntimeError("Input argument doesn't have a " +
        		" concrete type, instead is polymorphic: " + it);
      }
      SwiftType t = it.getAlternatives()[0];
      inVars.add(new Variable(t, inNames.get(i), VariableStorage.STACK,
                                    DefType.INARG, null));
    }
    return inVars;
  }
  
  public List<Variable> getOutVars() {
    ArrayList<Variable> outVars = new ArrayList<Variable>(outNames.size());
    for (int i = 0; i < outNames.size(); i++) {
      outVars.add(new Variable(ftype.getOutputs().get(i), outNames.get(i),
                             VariableStorage.STACK, DefType.OUTARG, null));
    }
    return outVars;
  }
}