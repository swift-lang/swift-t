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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Var.Alloc;

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
    private final Type keyType;
    private final Type memberType;

    public ArrayType(Type keyType, Type memberType) {
      super();
      this.keyType = keyType;
      this.memberType = memberType;
    }

    @Override
    public StructureType structureType() {
      return StructureType.ARRAY;
    }

    public Type keyType() {
      return keyType;
    }
    
    @Override
    public Type memberType() {
      return memberType;
    }


    @Override
    public String toString() {
      return memberType.toString() + "[" + keyType + "]";
    }

    @Override
    public String typeName() {
      return memberType.typeName() + "["  + keyType + "]";
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing ArrayType with non-type " +
                "object");
      }
      if (!(other instanceof ArrayType)) {
        return false;
      }
      ArrayType otherT = (ArrayType) other;
      return otherT.memberType.equals(memberType) &&
              otherT.keyType.equals(keyType);
    }

    @Override
    public int hashCode() {
      return memberType.hashCode() ^ ArrayType.class.hashCode();
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new ArrayType(keyType.bindTypeVars(vals),
                           memberType.bindTypeVars(vals));
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (Types.isArray(concrete)) {
        ArrayType concreteArray = (ArrayType)concrete.baseType();
        Map<String, Type> m1, m2;
        m1 = memberType.matchTypeVars(concreteArray.memberType);
        m2 = keyType.matchTypeVars(concreteArray.keyType);
        if (m1 == null || m2 == null) {
          return null;
        } else if (m1.isEmpty()) {
          // Common case
          return m2;
        } else if (m2.isEmpty()) {
          // Common case
          return m1;
        } else {
          return TypeVariable.unifyBindings(m1, m2);
        }
      }
      return null;
    }
    
    @Override
    public Type concretize(Type concrete) {
      assert(isArray(concrete));
      ArrayType concreteArray = (ArrayType)concrete.baseType();
      Type cMember = memberType.concretize(concreteArray.memberType());
      Type cKey = keyType.concretize(concreteArray.keyType);
      if (cMember == this.memberType && cKey == this.keyType)
        return this;
      return new ArrayType(cKey, cMember);
    }

    @Override
    public boolean assignableTo(Type other) {
      if (!isArray(other)) {
        return false;
      }
      ArrayType otherA = (ArrayType)other.baseType();
      // TODO
      // For now, types must exactly match, due to contra/co-variance issues
      // with type parameters. Need to check to see if member types
      // can be converted to other member types
      
      return keyType.matchTypeVars(otherA.keyType) != null &&
             memberType.matchTypeVars(otherA.memberType) != null;
    }

    @Override
    public boolean hasTypeVar() {
      return memberType.hasTypeVar();
    }

    @Override
    public Type getImplType() {
      Type implKey = keyType.getImplType();
      Type implMember = memberType.getImplType();
      if (implMember == memberType && implKey == keyType)
        return this;
      else if (implMember == null || implKey == null)
        return null;
      else
        return new ArrayType(implKey, implMember);
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
      concrete = concrete.stripSubTypes();
      if (concrete instanceof RefType) {
        Type concreteMember = ((RefType)concrete).referencedType;
        return referencedType.matchTypeVars(concreteMember);
      }
      return null;
    }
    
    @Override
    public Type concretize(Type concrete) {
      assert(isRef(concrete));
      Type cMember = referencedType.concretize(concrete.memberType());
      if (cMember == this.referencedType)
        return this;
      return new RefType(cMember);
    }

    @Override
    public boolean hasTypeVar() {
      return referencedType.hasTypeVar();
    }
    
    @Override
    public Type getImplType() {
      Type implMember = referencedType.getImplType();
      if (implMember == referencedType)
        return this;
      else if (implMember == null)
        return null;
      else
        return new RefType(implMember);
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
      concrete = concrete.stripSubTypes();
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public Type concretize(Type concrete) {
      assert(this.assignableTo(concrete));
      return this;
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
    
    
    @Override
    public Type getImplType() {
      return this;
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
      concrete = concrete.stripSubTypes();
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public Type concretize(Type concrete) {
      assert(this.assignableTo(concrete));
      return this;
    }
    
    @Override
    public boolean hasTypeVar() {
      return false;
    }
    
    @Override
    public Type getImplType() {
      return this;
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
      concrete = concrete.stripSubTypes();
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }

    @Override
    public Type concretize(Type concrete) {
      assert(this.assignableTo(concrete));
      return this;
    }
    
    @Override
    public boolean hasTypeVar() {
      return false;
    }
    
    @Override
    public Type getImplType() {
      return this;
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
      assert(isScalarUpdateable(upType));
      return new ScalarFutureType(upType.primType());
    }
    
    public static ScalarValueType asScalarValue(Type valType) {
      assert(isScalarUpdateable(valType));
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
      concrete = concrete.stripSubTypes();
      if (this.equals(concrete)) {
        return Collections.emptyMap();
      } else {
        return null;
      }
    }
    
    @Override
    public Type concretize(Type concrete) {
      assert(this.assignableTo(concrete));
      return this;
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
    
    @Override
    public Type getImplType() {
      return this;
    }
  }
  
  public static class UnionType extends Type {
    private final List<Type> alts;
    
    private UnionType(ArrayList<Type> alts) {
      // Shouldn't have single-element union type
      assert(alts.size() != 1);
      this.alts = Collections.unmodifiableList(alts);
    }

    public List<Type> getAlternatives() {
      return alts;
    }
    
    public static List<Type> getAlternatives(Type type) {
      if (isUnion(type)) {
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
      if (isUnion(other)) { 
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
    public Type concretize(Type concrete) {
      for (Type alt: alts) {
        if (alt.assignableTo(concrete)) {
          return alt;
        }
      }
      throw new STCRuntimeError("None of alt types: " + alts +
                                " matches concrete: " + concrete);
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
    
    @Override
    public Type getImplType() {
      return null;
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

    /**
     * Check two sets of bindings are compatible with each other
     * @param m1
     * @param m2
     */
    public static Map<String, Type> unifyBindings(Map<String, Type> m1,
                                                  Map<String, Type> m2) {

      Set<String> intersection = new HashSet<String>(m1.keySet());
      intersection.retainAll(m2.keySet());
      
      // All non-overlapping ones should be retained
      Map<String, Type> res = new HashMap<String, Type>();
      res.putAll(m1);
      res.putAll(m2);
      for (String overlapping: intersection) {
        res.remove(overlapping);
      }
      
      for (String match: intersection) {
        Type t1 = m1.get(match);
        Type t2 = m2.get(match);
        List<Type> compatible = typeIntersection(Arrays.asList(t1, t2));
        if (compatible.isEmpty()) {
          return null;
        }
        res.put(match, UnionType.makeUnion(compatible));
      }
      return res;
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
    public Type concretize(Type concrete) {
      throw new STCRuntimeError("Unbound type var when concretizing");
    }
    
    @Override
    public boolean hasTypeVar() {
      return true;
    }
    
    @Override
    public Type getImplType() {
      return null;
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
    public Type concretize(Type concrete) {
      // Fill in wildcard
      return concrete;
    }
    
    @Override
    public boolean assignableTo(Type other) {
      return true;
    }

    @Override
    public boolean hasTypeVar() {
      return false;
    }
    
    @Override
    public Type getImplType() {
      return null;
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
  public abstract static class Type implements Typed {
    
    /**
     * For Typed interface
     * @return
     */
    @Override
    public Type type() {
      return this;
    }
    
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
     * Return type with any subtypes stripped off
     */
    public Type stripSubTypes() {
      return this;
    }
    
    /**
     * @return true if any type variables in type
     */
    public abstract boolean hasTypeVar();

    /**
     * @return the base type which is used to implement this type.
     *        This will find the implementation type of any paremeter types.
     *          Null if not concrete type
     */
    public abstract Type getImplType();
    
    
    /**
     * Get the base type of this type.  This doesn't do anything
     * to any parameter types
     */
    public Type baseType() {
      // Default
      return this;
    }
    
    /**
     * @return true if the type is something we can actually instantiate
     */
    public boolean isConcrete() {
      return getImplType() != null;
    }

    /**
     * Convert to a concrete type that is assignable to the argument.
     * This will throw a runtime error if the type isn't assignable
     * to concrete
     * @param concrete
     * @return
     */
    public abstract Type concretize(Type concrete);

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
      if (!isFunction(concrete)) {
        return null;
      }      
      throw new STCRuntimeError("Not yet implemented: matching typevars for"
          + " function types");
    }
    
    @Override
    public Type concretize(Type concrete) {
      assert(Types.isFunction(concrete));
      FunctionType concreteF = (FunctionType)concrete;
      
      List<Type> concreteIn = new ArrayList<Type>(inputs.size());
      List<Type> concreteOut = new ArrayList<Type>(outputs.size());
      
      for (int i = 0; i < inputs.size(); i++) {
        Type in = this.inputs.get(i);
        Type cIn = concreteF.inputs.get(i);
        // To use a function of this type type in a place where a 
        // function of the concrete type is expected, must be able
        // to accept any args the concrete would
        assert(cIn.assignableTo(in));
        // TODO: this isn't quite right
        concreteIn.add(in.concretize(cIn));
      }
      
      for (int i = 0; i < outputs.size(); i++) {
        Type out = this.outputs.get(i);
        Type cOut = concreteF.outputs.get(i);
        assert(out.assignableTo(cOut));
        concreteOut.add(out.concretize(cOut));
      }
      // TODO: how to handle varargs?
      return new FunctionType(concreteIn, concreteOut, varargs);
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
    
    
    @Override
    public Type getImplType() {
      List<Type> ins = new ArrayList<Type>(inputs.size());
      List<Type> outs = new ArrayList<Type>(outputs.size());
      for (Type in: inputs) {
        Type implType = in.getImplType();
        if (implType == null)
          return null;
        ins.add(implType);
      }
      
      for (Type out: outputs) {
        Type implType = out.getImplType();
        if (implType == null)
          return null;
        outs.add(implType);
      }
      
      return new FunctionType(ins, outs, varargs);
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
      while (concrete instanceof SubType) {
        if (this.equals(concrete)) {
          break;
        } else {
          concrete = ((SubType)concrete).baseType();
        }
      }
      
      return baseType.matchTypeVars(concrete);
    }
    
    
    /**
     * Return type with any subtypes stripped off
     */
    public Type stripSubTypes() {
      return baseType.stripSubTypes();
    }
    
    @Override
    public Type concretize(Type concrete) {
      // Should match already
      assert(this.assignableTo(concrete));
      return this;
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
    
    public Type baseType() {
      // Default
      return baseType.baseType();
    }

    @Override
    public int hashCode() {
      return baseType.hashCode() ^ name.hashCode();
    }
    
  }
  
  /**
   * Interface for something that can be typechecked.  This is mainly
   * for convenience, allowing utility functions to accept different types 
   */
  public static interface Typed {
    public Type type();
  }
  
  public static Map<String, Type> getBuiltInTypes() {
    return Collections.unmodifiableMap(nativeTypes);
  }
  
  /**
   * Convenience function to check if a type is an array
   * @param t
   * @return
   */
  public static boolean isArray(Typed t) {
    return t.type().structureType() == StructureType.ARRAY;
  }

  /**
   * Convenience function to check if a type is a reference to an array
   * @param t
   * @return
   */
  public static boolean isArrayRef(Typed t) {
    return isRef(t) && Types.isArray(t.type().memberType());
  }

  /**
   * Convenience function to get member type of array or array ref
   */
  public static Type arrayMemberType(Typed arrayT) {
    if (isArray(arrayT)) {
      return arrayT.type().memberType();
    } else if (isArrayRef(arrayT)) {
      return arrayT.type().memberType().memberType();
    } else {
      throw new STCRuntimeError("called arrayMemberType on non-array"
          + " type " + arrayT.toString());
    }
  }

  public static boolean isMemberType(Typed member, Typed arr) {
    Type memberType = arrayMemberType(arr.type());
    return (member.type().assignableTo(memberType));
  }
  
  /**
   * @param member
   * @param arr
   * @return true if member is a reference to the member type of arr,
   *          false if it is the same as member type of arr
   * @throws STCRuntimeError if member can't be a member or ref to 
   *                                      member of array
   */
  public static boolean isMemberReference(Typed member,
                                          Typed arr) 
          throws STCRuntimeError{
    Type memberType = arrayMemberType(arr);
    if (memberType.equals(member.type())) {
      return false;
    } else if (isRefTo(member, memberType)) {
      return true;
    }
    throw new STCRuntimeError("Inconsistent types: array of type " 
        + arr.type() + " with member of type " + member.type());
  }

  public static Type arrayKeyType(Typed arr) {
    if (Types.isArray(arr)) {
      return ((ArrayType)arr.type().baseType()).keyType;
    } else {
      assert(Types.isArrayRef(arr)) : arr.type();
      return ((ArrayType)arr.type().baseType().memberType()).keyType;
    }
  }
  
  public static boolean isArrayKeyVal(Typed arr, Arg key) {
    // Interpret arg type as a value
    Type actual = key.typeInternal(false);
    // Get the value type of the array key
    Type expected = derefResultType(arrayKeyType(arr));
    return actual.assignableTo(expected);
  }
  

  /**
   * Return true if the type is possibly a valid array key.
   * @param keyType
   * @return
   */
  public static boolean isValidArrayKey(Typed keyType) {
    // Handle polymorphic types
    for (Type t: UnionType.getAlternatives(keyType.type())) {;
      if (isScalarFuture(keyType) && !isBlob(keyType)) {
        return true;
      } else if (isWildcard(t) || isTypeVar(t)) {
        return true;
      }
    }
    return false;
  }
  
  public static boolean isArrayKeyFuture(Typed arr, Typed key) {
    return key.type().assignableTo(arrayKeyType(arr));
  }
  

  /**
   * Return true if the type is one that we can subscribe to
   * the final value of 
   * @param waitExprType
   * @return
   */
  public static boolean canWaitForFinalize(Typed type) {
    return isFuture(type) || isScalarUpdateable(type) ||
           isArray(type);
  }
  
  public static boolean isFuture(Typed t) {
    return isScalarFuture(t) || isRef(t);
  }
  
  /**
   * Convenience function to check if a type is scalar
   * @param t
   * @return
   */
  public static boolean isScalarFuture(Typed t) {
    return t.type().structureType() == StructureType.SCALAR_FUTURE;
  }

  public static boolean isScalarValue(Typed t) {
    return t.type().structureType() == StructureType.SCALAR_VALUE;
  }
  
  public static boolean isScalarUpdateable(Typed t) {
    return t.type().structureType() == StructureType.SCALAR_UPDATEABLE;
  }

  public static boolean isRef(Typed t) {
    return t.type().structureType() == StructureType.REFERENCE;
  }

  public static boolean isStruct(Typed t) {
    return t.type().structureType() == StructureType.STRUCT;
  }
  
  public static boolean isStructRef(Typed t) {
    return isRef(t) && isStruct(t.type().memberType());
  }
  
  public static boolean isFuture(PrimType primType, Typed t) {
    return isScalarFuture(t) && t.type().primType() == primType;
  }
  
  public static boolean isVal(PrimType primType, Typed t) {
    return isScalarValue(t) && t.type().primType() == primType;
  }
  
  public static boolean isRef(PrimType primType, Typed t) {
    return isRef(t) && isFuture(primType, t.type().memberType());
  }

  public static boolean isBool(Typed t) {
    return isFuture(PrimType.BOOL, t);
  }
  
  public static boolean isBoolVal(Typed t) {
    return isVal(PrimType.BOOL, t);
  }
  
  public static boolean isBoolRef(Typed t) {
    return isRef(PrimType.BOOL, t);
  }
  
  public static boolean isInt(Typed t) {
    return isFuture(PrimType.INT, t);
  }
  
  public static boolean isIntVal(Typed t) {
    return isVal(PrimType.INT, t);
  }
  
  public static boolean isIntRef(Typed t) {
    return isRef(PrimType.INT, t);
  }

  public static boolean isFloat(Typed t) {
    return isFuture(PrimType.FLOAT, t);
  }
  
  public static boolean isFloatVal(Typed t) {
    return isVal(PrimType.FLOAT, t);
  }
  
  public static boolean isFloatRef(Typed t) {
    return isRef(PrimType.FLOAT, t);
  }

  public static boolean isString(Typed t) {
    return isFuture(PrimType.STRING, t);
  }
  
  public static boolean isStringVal(Typed t) {
    return isVal(PrimType.STRING, t);
  }
  
  public static boolean isStringRef(Typed t) {
    return isRef(PrimType.STRING, t);
  }
  
  public static boolean isVoid(Typed t) {
    return isFuture(PrimType.VOID, t);
  }
  
  public static boolean isVoidVal(Typed t) {
    return isVal(PrimType.VOID, t);
  }
  
  public static boolean isVoidRef(Typed t) {
    return isRef(PrimType.VOID, t);
  }

  public static boolean isFile(Typed t) {
    return isFuture(PrimType.FILE, t);
  }
  
  public static boolean isFileVal(Typed t) {
    return isVal(PrimType.FILE, t);
  }
  
  public static boolean isFileRef(Typed t) {
    return isRef(PrimType.FILE, t);
  }
  
  public static boolean isBlob(Typed t) {
    return isFuture(PrimType.BLOB, t);
  }
  
  public static boolean isBlobVal(Typed t) {
    return isVal(PrimType.BLOB, t);
  }
  
  public static boolean isBlobRef(Typed t) {
    return isRef(PrimType.BLOB, t);
  }

  public static boolean isRefTo(Typed refType, Typed valType) {
    return isRef(refType) && 
           refType.type().memberType().equals(valType.type());
  }
  
  public static boolean isAssignableRefTo(Typed refType, Typed valType) {
    return isRef(refType) && 
        refType.type().memberType().assignableTo(valType.type());
  }
  
  public static boolean isUpdateableEquiv(Typed up,
                                          Typed future) {
    return isScalarFuture(future) && isScalarUpdateable(up) && 
              future.type().primType() == up.type().primType();
  }
  
  public static Type derefResultType(Typed t) {
    if (isScalarFuture(t) || isScalarUpdateable(t))  {
      return new ScalarValueType(t.type().primType());
    } else if (isRef(t)) {
      return t.type().memberType();
    } else {
      throw new STCRuntimeError(t.type() + " can't be dereferenced");
    }
  }
  
  /**
   * Is it a type we can map to a file?
   */
  public static boolean isMappable(Typed t) {
    // We can only map files right now..
    return t.type().assignableTo(F_FILE);
  }

  public static boolean isUnion(Typed type) {
    return type.type().structureType() == StructureType.TYPE_UNION;
  }
  
  public static boolean isTypeVar(Typed type) {
    return type.type().structureType() == StructureType.TYPE_VARIABLE;
  }

  public static boolean isPolymorphic(Typed type) {
    return isUnion(type) || isTypeVar(type) || isWildcard(type);
  }
  
  public static boolean isFunction(Typed type) {
    return type.type().structureType() == StructureType.FUNCTION;
  }

  public static boolean isWildcard(Typed argType) {
    return argType.type().structureType() == StructureType.WILDCARD;
  }

  public static boolean isPiecewiseAssigned(Typed type) {
    return isArray(type) || isArrayRef(type) || isStruct(type);
  }

  /**
   * @param type
   * @return true if there is a side-channel of information in the variable
   *            that needs to be read
   */
  public static boolean hasReadableSideChannel(Typed type) {
    // Currently only file is in this category
    return isFile(type);
  }

  
  /**
   * Returns true if the variable requires initialization before
   * being used in input context
   * @param output
   * @return
   */
  public static boolean inputRequiresInitialization(Var input) {
    return input.storage() == Alloc.ALIAS 
        || Types.isScalarUpdateable(input);
  }
  
  /**
   * Returns true if the variable requires initialiation before being used
   * in output context
   * @param output
   * @return
   */
  public static boolean outputRequiresInitialization(Var output) {
    return output.storage() == Alloc.ALIAS 
        || isScalarUpdateable(output)
        || isFileVal(output);
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
  
  public static boolean listsEqual(List<? extends Typed> a,
                                   List<? extends Typed> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).type().equals(b.get(i).type())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Find the intersection of a list of types where each element
   * is either a UnionType or a different non-union type
   * @param types
   * @return a list of types in intersection, in order of appearance in
   *         first type
   */
  public static List<Type> typeIntersection(List<Type> types) {
    if(types.size() == 0) {
      return Collections.<Type>singletonList(new WildcardType());
    }
    // Shortcircuit common cases
    if (types.size() == 1 ||
        (types.size() == 2 && types.get(0).equals(types.get(1)))) {
      return UnionType.getAlternatives(types.get(0));
    }
    
    boolean sawWildcard = true;
    Set<Type> intersection = null;
    for (Type argType: types) {
      if (Types.isWildcard(argType)) {
        // do nothing
        sawWildcard = true;
      } else if (intersection == null) {
        intersection = new HashSet<Type>();
        intersection.addAll(UnionType.getAlternatives(argType));
      } else {
        Iterator<Type> it = intersection.iterator();
        while (it.hasNext()) {
          Type t1 = it.next();
          boolean compatible = false;
          for (Type t2: UnionType.getAlternatives(argType)) {
            // Check to see if types are compatible
            Map<String, Type> requiredMappings = t2.matchTypeVars(t1);
            if (requiredMappings != null && requiredMappings.size() == 0) {
              compatible = true;
            }
            requiredMappings = t1.matchTypeVars(t2);
            if (requiredMappings != null && requiredMappings.size() == 0) {
              compatible = true;
            }
          } 
          
          if (!compatible) {
            it.remove();
          }
        }
      }
    }
    
    if (intersection == null) {
      assert(sawWildcard);
      return Collections.<Type>singletonList(new WildcardType());
    }
    
    // Make sure alternatives in original order
    ArrayList<Type> result = new ArrayList<Type>();
    for (Type alt: UnionType.getAlternatives(types.get(0))) {
      if (intersection.contains(alt)) {
        result.add(alt);
      }
    }
    return result;
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
  
  /**
   * Represents location of execution 
   */
  public static final Type F_LOCATION = new SubType(F_INT, "location");
  public static final Type V_LOCATION = V_INT; // Internally is int

  
  private static final String VALUE_SIGIL = "$";

  private static final Map<String, Type> nativeTypes 
                                    = new HashMap<String, Type>();

  static {
    registerPrimitiveTypes();
  }

  /**
   * Register primitive types that can be referred to in Swift
   * code by name.
   */
  private static void registerPrimitiveTypes() {
    registerPrimitiveType(F_INT);
    registerPrimitiveType(F_STRING);
    registerPrimitiveType(F_FLOAT);
    registerPrimitiveType(F_BOOL);
    registerPrimitiveType(F_VOID);
    registerPrimitiveType(F_BLOB);
    registerPrimitiveType(F_FILE);
    registerPrimitiveType(UP_FLOAT);
    registerPrimitiveType(F_LOCATION);
  }
  
  public static void registerPrimitiveType(Type type) {
    String name = type.typeName();
    assert(!nativeTypes.containsKey(name)): name;
    nativeTypes.put(name, type);
  }

}
