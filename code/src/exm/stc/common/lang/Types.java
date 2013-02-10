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
package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * This module provides the type definitions used for Swift,
 * along with convenience functions for creating, checking and
 * manipulating types.  
 * 
 * The base class for variable types is Type.
 * Since we don't yet have first class functions, FunctionType is separate
 * from the variable type system.
 */
public class Types {

  public static class ArrayType extends Type {
    private final Type memberType;

    public ArrayType(Type memberType) {
      super();
      this.memberType = memberType;
    }

    @Override
    public StructureType structureType() {
      return StructureType.ARRAY;
    }

    @Override
    public Type memberType() {
      return memberType;
    }


    @Override
    public String toString() {
      return memberType.toString() + "[]";
    }

    @Override
    public String typeName() {
      return memberType.typeName() + "[]";
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ArrayType with non-type " +
                "object");
      }
      Type otherT = (Type) other;
      if (!otherT.structureType().equals(StructureType.ARRAY)) {
        return false;
      } else {
        return otherT.memberType().equals(memberType);
      }
    }

    @Override
    public int hashCode() {
      return memberType.hashCode() ^ ArrayType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new ArrayType(memberType.bindTypeVars(vals));
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (concrete instanceof ArrayType) {
        return memberType.matchTypeVars(((ArrayType)concrete).memberType);
      }
      return null;
    }

    @Override
    public boolean assignableTo(Type other) {
      return other.structureType() == StructureType.ARRAY &&
            memberType.assignableTo(other.memberType());
    }

    @Override
    public boolean hasTypeVar() {
      return memberType.hasTypeVar();
    }

  }

  public enum PrimType
  {
    INT, STRING, FLOAT, BOOL, 
    BLOB, FILE,
     // Void is a type with no information (i.e. the external type in Swift, or 
     // the unit type in functional languages)
    VOID; 

    static String toString(PrimType pt) {
      if (pt == INT) {
        return "int";
      } else if (pt == STRING) {
        return "string";
      } else if (pt == FLOAT) {
        return "float";
      } else if (pt == BOOL) {
        return "boolean";
      } else if (pt == VOID) {
        return "void";
      } else if (pt == BLOB) {
        return "blob";
      } else if (pt == FILE) {
        return "file";
      } else {
        return "UNKNOWN_TYPE";
      }
    }

  }

  public static class RefType extends Type {
    private final Type referencedType;
    public RefType(Type referencedType) {
      this.referencedType = referencedType;
    }

    @Override
    public StructureType structureType() {
      return StructureType.REFERENCE;
    }
    @Override
    public Type memberType() {
      return referencedType;
    }

    @Override
    public String toString() {
      return "*(" + referencedType.toString() + ")";
    }

    @Override
    public String typeName() {
      return "*(" + referencedType.typeName() + ")";
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ReferenceType with " +
              "non-type object");
      }
      Type otherT = (Type) other;
      if (!(otherT instanceof RefType)) {
        return false;
      } else {
        return otherT.memberType().equals(referencedType);
      }
    }

    @Override
    public int hashCode() {
      return referencedType.hashCode() ^ RefType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new RefType(referencedType.bindTypeVars(vals));
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (concrete instanceof RefType) {
        Type concreteMember = ((RefType)concrete).referencedType;
        return referencedType.matchTypeVars(concreteMember);
      }
      return null;
    }

    @Override
    public boolean hasTypeVar() {
      return referencedType.hasTypeVar();
    }
  }

  public static class StructType extends Type {
    public static class StructField {
      private final Type type;
      private final String name;
      public StructField(Type type, String name) {
        this.type = type;
        this.name = name;
      }

      public Type getType() {
        return type;
      }

      public String getName() {
        return name;
      }
    }

    public StructType(String typeName, List<StructField> fields) {
      this.typeName = typeName;
      this.fields = new ArrayList<StructField>(fields);
    }

    private final List<StructField> fields;
    private final String typeName;

    public String getTypeName() {
      return typeName;
    }

    @Override
    public StructureType structureType() {
      return StructureType.STRUCT;
    }

    public List<StructField> getFields() {
      return Collections.unmodifiableList(fields);
    }

    public int getFieldCount() {
      return fields.size();
    }


    public Type getFieldTypeByName(String name) {
      for (StructField field: fields) {
        if (field.getName().equals(name)) {
          return field.getType();
        }
      }
      return null;
    }

    public int getFieldIndexByName(String name) {
      for (int i=0; i < fields.size(); i++) {
        StructField field = fields.get(i);
        if (field.getName().equals(name)) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ReferenceType with " +
              "non-type object");
      }
      Type otherT = (Type) other;
      if (!otherT.structureType().equals(StructureType.STRUCT)) {
        return false;
      } else {
        // Type names should match, along with fields
        StructType otherST = (StructType)otherT;
        if (!otherST.getTypeName().equals(typeName)) {
          return false;
        } else {
          // assume that if the names match, the types match, because the
          // language doesn't permit the same type name to be used twice
          return true;
        }
      }
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder("struct " + this.typeName + " {");
      boolean first = true;
      for (StructField f: fields) {
        if (first) {
          first = false;
        } else {
          s.append("; ");
        }
        s.append(f.getType().toString());
        s.append(' ');
        s.append(f.getName());
      }
      s.append("}");
      return s.toString();
    }

    @Override
    public String typeName() {
      return this.typeName;
    }

    @Override
    public int hashCode() {
      return StructType.class.hashCode() ^ typeName.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      // Assume no type variables inside struct
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public boolean hasTypeVar() {
      for (StructField field: fields) {
        if (field.type.hasTypeVar()) {
          return true;
        }
      }
      return false;
    }
  }

  public static class ScalarValueType extends Type {
    private final PrimType type;

    public ScalarValueType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_VALUE;
    }

    @Override
    public PrimType primType() {
      return this.type;
    }

    @Override
    public String toString() {
      return typeName();
    }

    @Override
    public String typeName() {
      return VALUE_SIGIL + PrimType.toString(type);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ScalarValueType with "
            + "non-type object");
      }
      Type otherT = (Type) other;
      if (otherT instanceof ScalarValueType) {
        return otherT.primType().equals(this.type);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarValueType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
  }


  public static class ScalarFutureType extends Type {
    private final PrimType type;
    public ScalarFutureType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_FUTURE;
    }

    @Override
    public PrimType primType() {
      return this.type;
    }

    @Override
    public String toString() {
      return typeName();
    }

    /**
     * Prepend prim type name with $ to indicate it is the value of
     * the type, rather than a future
     */
    @Override
    public String typeName() {
      return PrimType.toString(type);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ScalarFutureType with non-type "
            + "object");
      }
      Type otherT = (Type) other;
      if (otherT instanceof ScalarFutureType) {
        return otherT.primType() == this.type;
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarFutureType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
  }

  public static class ScalarUpdateableType extends Type {
    private final PrimType type;
    public ScalarUpdateableType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_UPDATEABLE;
    }

    @Override
    public PrimType primType() {
      return this.type;
    }

    @Override
    public String toString() {
      return typeName();
    }

    /**
     * 
     */
    @Override
    public String typeName() {
      return "updateable_" + PrimType.toString(type);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ScalarUpdateableType " +
            "with non-type object");
      }
      Type otherT = (Type) other;
      if (otherT instanceof ScalarUpdateableType) {
        return otherT.primType().equals(this.type);
      } else {
        return false;
      }
    }

    public static ScalarFutureType asScalarFuture(Type upType) {
      assert(Types.isScalarUpdateable(upType));
      return new ScalarFutureType(upType.primType());
    }
    
    public static ScalarValueType asScalarValue(Type valType) {
      assert(Types.isScalarUpdateable(valType));
      return new ScalarValueType(valType.primType());
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarUpdateableType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
  }
  
  public static class UnionType extends Type {
    private final List<Type> alts;
    
    private UnionType(ArrayList<Type> alts) {
      this.alts = Collections.unmodifiableList(alts);
    }

    public List<Type> getAlternatives() {
      return alts;
    }
    
    public static List<Type> getAlternatives(Type type) {
      if (Types.isUnion(type)) {
        return ((UnionType)type).getAlternatives();
      } else {
        return Collections.singletonList(type);
      }
    }
    
    /**
     * @return UnionType if multiple types, or plain type if singular
     */
    public static Type makeUnion(List<Type> alts) {
      if (alts.size() == 1) {
        return alts.get(0);
      } else {
        return new UnionType(new ArrayList<Type>(alts));
      }
    }
    
    public static Type createUnionType(Type ...alts) {
      if (alts.length == 1) {
        return alts[0];
      } else {
        ArrayList<Type> list = new ArrayList<Type>();
        for (Type alt: alts) {
          list.add(alt);
        }
        return new UnionType(list);
      }
    }

    @Override
    public StructureType structureType() {
      return StructureType.TYPE_UNION;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      sb.append("(");
      for (Type alt: alts) {
        if (first) {
          first = false;
        } else {
          sb.append("|");
        }
        sb.append(alt.toString());
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    public String typeName() {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      sb.append("(");
      for (Type alt: alts) {
        if (first) {
          first = false;
        } else {
          sb.append("|");
        }
        sb.append(alt.typeName());
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing UnionType " +
            "with non-type object");
      }
      Type otherT = (Type) other;
      if (otherT.structureType() != StructureType.TYPE_UNION) {
        return false;
      } else {
        UnionType otherUT = (UnionType)otherT;
        // Sets must be same size
        if (this.alts.size() != otherUT.alts.size()) {
          return false;
        }
        
        // Check members are the same
        for (Type alt: this.alts) {
          if (!otherUT.alts.contains(alt)) {
            return false;
          }
        }
        return true;
      }
    }

    @Override
    public boolean assignableTo(Type other) {
      if (Types.isUnion(other)) { 
        UnionType otherUnion = (UnionType)other;
        // Check if subset
        for (Type alt: this.alts) {
          if (!otherUnion.alts.contains(alt)) {
            return false;
          }
        }
        return true;
      } else {
        return alts.contains(other);
      }
    }

    @Override
    public int hashCode() {
      int hash = UnionType.class.hashCode();
      for (Type alt: alts) {
        // Iteration order doesn't matter for xor
        hash ^= alt.hashCode();
      }
      return hash;
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      ArrayList<Type> boundAlts = new ArrayList<Type>(alts.size());
      for (Type alt: alts) {
        boundAlts.add(alt.bindTypeVars(vals));
      }
      return new UnionType(boundAlts);
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      throw new STCRuntimeError("Not yet implemented: matching typevars for"
          + " union types");
    }

    @Override
    public boolean hasTypeVar() {
      for (Type alt: alts) {
        if (alt.hasTypeVar()) {
          return true;
        }
      }
      return false;
    }
  }
  
  /**
   * A type variable that represents a wildcard type
   */
  public static class TypeVariable extends Type {
    private final String typeVarName;

    public TypeVariable(String typeVarName) {
      super();
      this.typeVarName = typeVarName;
    }

    @Override
    public StructureType structureType() {
      return StructureType.TYPE_VARIABLE;
    }

    @Override
    public String toString() {
      return typeName();
    }

    @Override
    public String typeName() {
      return typeVarName;
    }
    
    @Override
    public String typeVarName() {
      return typeVarName;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Type)) {
        throw new STCRuntimeError("Comparing TypeVariable " +
            "with non-type object");
      }
      if (((Type)obj).structureType() == StructureType.TYPE_VARIABLE) {
        TypeVariable other = (TypeVariable) obj;
        return this.typeVarName.equals(other.typeVarName);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return typeVarName.hashCode() ^ TypeVariable.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      Type binding = vals.get(typeVarName);
      if (binding == null) {
        return this;
      } else {
        return binding;
      }
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      return Collections.singletonMap(typeVarName, concrete);
    }

    @Override
    public boolean hasTypeVar() {
      return true;
    }
  }
  
  public static class WildcardType extends Type {

    @Override
    public StructureType structureType() {
      return StructureType.WILDCARD;
    }

    @Override
    public String toString() {
      return typeName();
    }

    @Override
    public String typeName() {
      return "?";
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Type)) {
        return false;
      } else {
        return ((Type)o).structureType() == StructureType.WILDCARD;
      }
    }

    @Override
    public int hashCode() {
      return WildcardType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      return Collections.emptyMap();
    }

    @Override
    public boolean assignableTo(Type other) {
      return true;
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
    
  }
  
  private enum StructureType
  {
    SCALAR_UPDATEABLE,
    SCALAR_FUTURE,
    SCALAR_VALUE,
    ARRAY,
    /** Reference is only used internally in compiler */
    REFERENCE,
    STRUCT,
    TYPE_VARIABLE,
    WILDCARD,
    TYPE_UNION,
    FUNCTION,
  }

  /**
   * Class to represent a complex swift type, which can be recursively
   * constructed out of scalar types, arrays, and structures.
   *
   */
  public abstract static class Type {
    public abstract StructureType structureType();

    /**
     * Get the primitive type (only valid if scalar)
     * @return
     */
    public PrimType primType() {
      throw new STCRuntimeError("primitiveType() not implemented " +
      "for class " + getClass().getName());
    }

    public Type memberType() {
      throw new STCRuntimeError("memberType() not implemented " +
      "for class " + getClass().getName());
    }

    /** Prints out a description of type for user */
    @Override
    public abstract String toString();

    /** Print out a short unique name for type */
    public abstract String typeName();
    
    /** equals is required */
    @Override
    public abstract boolean equals(Object o);
    
    /**
     * Check if this type can be assigned to other type.
     * By default this is only if types are equal
     * In the case of union types, true if other is member
     * of union
     * @param other
     * @return
     */
    public boolean assignableTo(Type other) {
      return equals(other);
    }
    
    /** hashcode is required */
    @Override
    public abstract int hashCode();

    /**
     * Bind any type variables provided in map
     * @param vals type variable bindings
     * @return new type with bound variables
     */
    public abstract Type bindTypeVars(Map<String, Type> vals);
    
    /**
     * Match up any typevars in this type to vars in a concrete type
     * and return the type var binding. Returns null if types can't be matched
     */
    public abstract Map<String, Type> matchTypeVars(Type concrete);
    
    /**
     * @return true if any type variables in type
     */
    public abstract boolean hasTypeVar();

    /**
     * @return the base type which is used to implement this type
     */
    public Type getImplType() {
      return this;
    }
    
    public String typeVarName() {
      throw new STCRuntimeError("typeVarName() not supported for type "
                              + toString());
    }
  }

  /**
   * Function types are kept distinct from value types
   */
  public static class FunctionType extends Type {

    private final ArrayList<Type> inputs = new ArrayList<Type>();
    private final ArrayList<Type> outputs = new ArrayList<Type>();
    private final ArrayList<String> typeVars = new ArrayList<String>();

    /** if varargs is true, the final argument can be repeated many times */
    private final boolean varargs;

    public FunctionType(List<Type> inputs, List<Type> outputs,
                                                boolean varargs) {
      this(inputs, outputs, varargs, null);
    }
    public FunctionType(List<Type> inputs, List<Type> outputs,
          boolean varargs, Collection<String> typeVars) {
      this.inputs.addAll(inputs);
      this.outputs.addAll(outputs);
      this.varargs = varargs;
      if (typeVars != null) {
        this.typeVars.addAll(typeVars);
        Collections.sort(this.typeVars);
      }
    }

    public List<Type> getInputs() {
      return Collections.unmodifiableList(inputs);
    }

    public List<Type> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    public List<String> getTypeVars() {
      return Collections.unmodifiableList(typeVars);
    }

    public boolean hasVarargs() {
      return varargs;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(128);
      sb.append('(');
      for (Iterator<Type> it = inputs.iterator(); it.hasNext(); ) {
        Type t = it.next();
        sb.append(t);
        if (it.hasNext())
          sb.append(',');
      }
      sb.append(") -> (");
      for (Iterator<Type> it = outputs.iterator();
           it.hasNext(); ) {
        Type t = it.next();
        sb.append(t);
        if (it.hasNext())
          sb.append(',');
      }
      sb.append(')');
      return sb.toString();
    }

    @Override
    public StructureType structureType() {
      return StructureType.FUNCTION;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Type)) {
        throw new STCRuntimeError("Comparing FunctionType " +
            "with non-type object");
      }
      if (((Type)obj).structureType() == StructureType.FUNCTION) {
        FunctionType other = (FunctionType) obj;
        // All fields should match
        if (!(this.inputs.size() == other.inputs.size() &&
              this.outputs.size() == other.outputs.size() &&
              this.varargs == other.varargs)) {
          return false;
        }
        return listsEqual(this.inputs, other.inputs) &&
               listsEqual(this.outputs, other.outputs);
      }
      return false;
    }

    @Override
    public String typeName() {
      // TODO: canonical way to show type?
      return toString();
    }

    @Override
    public int hashCode() {
      int code = FunctionType.class.hashCode();
      for (Type t: inputs) {
        code ^= t.hashCode();
      }
      for (Type t: outputs) {
        code ^= t.hashCode();
      }
      code ^= ((Boolean)varargs).hashCode();
      return code;
    }
    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      List<Type> boundInputs = new ArrayList<Type>();
      for (Type input: inputs) {
        boundInputs.add(input.bindTypeVars(vals));
      }
      
      List<Type> boundOutputs = new ArrayList<Type>();
      for (Type output: outputs) {
        boundOutputs.add(output.bindTypeVars(vals));
      }
      
      return new FunctionType(boundInputs, boundOutputs, varargs);
    }
    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      throw new STCRuntimeError("Not yet implemented: matching typevars for"
          + " function types");
    }
    @Override
    public boolean hasTypeVar() {
      for (Type input: inputs) {
        if (input.hasTypeVar()) {
          return true;
        }
      }
      for (Type output: outputs) {
        if (output.hasTypeVar()) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Represents expression type.
   * 
   * Can have multiple elements because of multiple return valued functions
   */
  public static class ExprType {
    private final ArrayList<Type> types;

    public ExprType(List<Type> types) {
      this.types = new ArrayList<Type>(types);
    }

    public ExprType(Type exprType) {
      this.types = new ArrayList<Type>(1);
      this.types.add(exprType);
    }

    public List<Type> getTypes() {
      return Collections.unmodifiableList(types);
    }
    
    public Type get(int index) {
      return types.get(index);
    }

    public int elems() {
      return types.size();
    }
    
    @Override
    public String toString() {
      return types.toString();
    }
  }
  
  public static class SubType extends Type {
    private final Type baseType;
    private final String name;
    
    public SubType(Type baseType, String name) {
      super();
      this.baseType = baseType;
      this.name = name;
    }
    
    @Override
    public StructureType structureType() {
      return baseType.structureType();
    }
    @Override
    public PrimType primType() {
      return baseType.primType();
    }
    @Override
    public Type memberType() {
      return baseType.memberType();
    }
    @Override
    public boolean assignableTo(Type other) {
      // Is assignable to anything baseType is 
      // assignable to, plus any instance of this
      return baseType.assignableTo(other) ||
              this.equals(other);
    }
    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new SubType(baseType.bindTypeVars(vals), name);
    }
    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      return baseType.matchTypeVars(concrete);
    }
    @Override
    public boolean hasTypeVar() {
      return baseType.hasTypeVar();
    }

    @Override
    public String toString() {
      return typeName();
    }

    @Override
    public String typeName() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SubType)) {
        return false;
      }
      SubType ot = (SubType)o;
      return ot.name.equals(name) &&
              baseType.equals(ot.baseType);
    }
    
    @Override
    public Type getImplType() {
      // This has same implementation as base type
      return baseType.getImplType();
    }

    @Override
    public int hashCode() {
      return baseType.hashCode() ^ name.hashCode();
    }
    
  }
  
  public static Map<String, Type> getBuiltInTypes() {
    return Collections.unmodifiableMap(nativeTypes);
  }

  /**
   * Convenience function to check if a type is an array
   * @param t
   * @return
   */
  public static boolean isArray(Type t) {
    return t.structureType() == StructureType.ARRAY;
  }

  /**
   * Convenience function to check if a type is a reference to an array
   * @param t
   * @return
   */
  public static boolean isArrayRef(Type t) {
    return t.structureType() == StructureType.REFERENCE &&
        t.memberType().structureType() == StructureType.ARRAY;
  }

  /**
   * Convenience function to get member type of array or array ref
   */
  public static Type getArrayMemberType(Type arrayT) {
    if (isArray(arrayT)) {
      return arrayT.memberType();
    } else if (isArrayRef(arrayT)) {
      return arrayT.memberType().memberType();
    } else {
      throw new STCRuntimeError("called getArrayMemberType on non-array"
          + " type " + arrayT.toString());
    }
  }

  
  public static boolean isFuture(Type t) {
    return isScalarFuture(t) || isRef(t);
  }
  
  /**
   * Convenience function to check if a type is scalar
   * @param t
   * @return
   */
  public static boolean isScalarFuture(Type t) {
    return t.structureType() == StructureType.SCALAR_FUTURE;
  }

  public static boolean isScalarValue(Type t) {
    return t.structureType() == StructureType.SCALAR_VALUE;
  }
  
  public static boolean isScalarUpdateable(Type t) {
    return t.structureType() == StructureType.SCALAR_UPDATEABLE;
  }

  public static boolean isRef(Type t) {
    return t.structureType() == StructureType.REFERENCE;
  }

  public static boolean isStruct(Type t) {
    return t.structureType() == StructureType.STRUCT;
  }
  
  public static boolean isStructRef(Type t) {
    return t.structureType() == StructureType.REFERENCE 
                            && Types.isStruct(t.memberType());
  }
  
  public static boolean isFileRef(Type t) {
    return t.structureType() == StructureType.REFERENCE
        && Types.isFile(t.memberType());
  }

  public static boolean isBool(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.BOOL;
  }
  
  public static boolean isInt(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.INT;
  }

  public static boolean isFloat(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.FLOAT;
  }

  public static boolean isString(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.STRING;
  }
  
  public static boolean isVoid(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.VOID;
  }

  public static boolean isFile(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.FILE;
  }
  
  public static boolean isBlob(Type t) {
    return isScalarFuture(t) && t.primType() == PrimType.BLOB;
  }

  public static boolean isRefTo(Type refType, Type valType) {
    return isRef(refType) && refType.memberType().equals(valType);
  }
  
  public static boolean isUpdateableEquiv(Type up, Type future) {
    return isScalarFuture(future) && isScalarUpdateable(up) && 
                      future.primType() == up.primType();
  }
  
  public static Type derefResultType(Type future) {
    if (future.structureType() == StructureType.SCALAR_FUTURE
          || future.structureType() == StructureType.SCALAR_UPDATEABLE) {
      return new ScalarValueType(future.primType());
    } else if (future.structureType() == StructureType.REFERENCE) {
      return future.memberType();
    } else {
      throw new STCRuntimeError(future.toString() + " can't "
          + " be dereferenced");
    }
    
  }
  
  /**
   * Is it a type we can map to a file?
   */
  public static boolean isMappable(Type t) {
    // We can only map files right now..
    return t.equals(F_FILE);
  }

  public static boolean isUnion(Type type) {
    return type.structureType() == StructureType.TYPE_UNION;
  }
  
  public static boolean isTypeVar(Type type) {
    return type.structureType() == StructureType.TYPE_VARIABLE;
  }

  public static boolean isPolymorphic(Type type) {
    return type.structureType() == StructureType.TYPE_UNION ||
        type.structureType() == StructureType.TYPE_VARIABLE;
  }
  
  public static boolean isFunction(Type type) {
    return type.structureType() == StructureType.FUNCTION;
  }

  public static boolean isWildcard(Type argType) {
    return argType.structureType() == StructureType.WILDCARD;
  }

  public static boolean isPiecewiseAssigned(Type type) {
    return Types.isArray(type) || Types.isArrayRef(type) ||
           Types.isStruct(type);
  }
  
  /** 
   * More convenient way of representing array types for some analysies
   *
   */
  public static class ArrayInfo {
    public ArrayInfo(Type baseType, int nesting) {
      super();
      this.baseType = baseType;
      this.nesting = nesting;
    }
    /**
     * Construct from regular SwiftType
     * @param type
     */
    public ArrayInfo(Type type) {
      assert(type.structureType() == StructureType.ARRAY);
      int depth = 0;
      while (type.structureType() == StructureType.ARRAY) {
        type = type.memberType();
        depth++;
      }
      this.nesting = depth;
      this.baseType = type;
    }
    
    public final Type baseType; 
    public final int nesting;
  }
  
  public static boolean listsEqual(List<Type> a, List<Type> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static final Type F_INT = new ScalarFutureType(PrimType.INT);
  public static final Type V_INT = new ScalarValueType(PrimType.INT);
  public static final Type R_INT = new RefType(F_INT);

  public static final Type F_STRING = new ScalarFutureType(PrimType.STRING);
  public static final Type V_STRING = new ScalarValueType(PrimType.STRING);
  public static final Type R_STRING = new RefType(F_STRING);

  public static final Type F_FLOAT = new ScalarFutureType(PrimType.FLOAT);
  public static final Type V_FLOAT = new ScalarValueType(PrimType.FLOAT);
  public static final Type R_FLOAT = new RefType(F_FLOAT);
  public static final Type UP_FLOAT = new ScalarUpdateableType(PrimType.FLOAT);

  public static final Type F_BOOL = new ScalarFutureType(PrimType.BOOL);
  public static final Type V_BOOL = new ScalarValueType(PrimType.BOOL);
  public static final Type R_BOOL = new RefType(F_BOOL);
  
  public static final Type F_BLOB = new ScalarFutureType(PrimType.BLOB);
  public static final Type R_BLOB = new RefType(F_BLOB);
  public static final Type V_BLOB =
      new ScalarValueType(PrimType.BLOB);   
  public static final Type F_FILE = new ScalarFutureType(PrimType.FILE);
  public static final Type V_FILE = new ScalarValueType(PrimType.FILE);
  public static final Type REF_FILE = new RefType(F_FILE);
  
  public static final Type V_VOID = new ScalarValueType(PrimType.VOID);
  public static final Type F_VOID = new ScalarFutureType(PrimType.VOID);
  public static final Type REF_VOID = new RefType(F_VOID);
  
  private static final String VALUE_SIGIL = "$";

  private static final Map<String, Type> nativeTypes;

  static {
    nativeTypes = new HashMap<String, Type>();
    nativeTypes.put("int", Types.F_INT);
    nativeTypes.put("string", Types.F_STRING);
    nativeTypes.put("float", Types.F_FLOAT);
    nativeTypes.put("boolean", Types.F_BOOL);
    nativeTypes.put("void", Types.F_VOID);
    nativeTypes.put("blob", Types.F_BLOB);
    nativeTypes.put("file", Types.F_FILE);
    nativeTypes.put("updateable_float", Types.UP_FLOAT);
  }

}
