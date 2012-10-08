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
 * The base class for variable types is SwiftType.
 * Since we don't yet have first class functions, FunctionType is separate
 * from the variable type system.
 */
public class Types {

  public static class ArrayType extends SwiftType {
    private final SwiftType memberType;

    public ArrayType(SwiftType memberType) {
      super();
      this.memberType = memberType;
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.ARRAY;
    }

    @Override
    public SwiftType getMemberType() {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ArrayType with non-type " +
        		"object");
      }
      SwiftType otherT = (SwiftType) other;
      if (!otherT.getStructureType().equals(StructureType.ARRAY)) {
        return false;
      } else {
        return otherT.getMemberType().equals(memberType);
      }
    }

    @Override
    public int hashCode() {
      return memberType.hashCode() ^ ArrayType.class.hashCode();
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      return new ArrayType(memberType.bindTypeVars(vals));
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
      if (concrete instanceof ArrayType) {
        return memberType.matchTypeVars(((ArrayType)concrete).memberType);
      }
      return null;
    }

    @Override
    public boolean hasTypeVar() {
      return memberType.hasTypeVar();
    }

  }

  public enum PrimType
  {
    INTEGER, STRING, FLOAT, BOOLEAN, 
    BLOB, FILE,
     // Void is a type with no information (i.e. the external type in Swift, or 
     // the unit type in functional languages)
    VOID; 

    static String toString(PrimType pt) {
      if (pt == INTEGER) {
        return "int";
      } else if (pt == STRING) {
        return "string";
      } else if (pt == FLOAT) {
        return "float";
      } else if (pt == BOOLEAN) {
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

  public static class ReferenceType extends SwiftType {
    private final SwiftType referencedType;
    public ReferenceType(SwiftType referencedType) {
      this.referencedType = referencedType;
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.REFERENCE;
    }
    @Override
    public SwiftType getMemberType() {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ReferenceType with " +
              "non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (!otherT.getStructureType().equals(StructureType.REFERENCE)) {
        return false;
      } else {
        return otherT.getMemberType().equals(referencedType);
      }
    }

    @Override
    public int hashCode() {
      return referencedType.hashCode() ^ ReferenceType.class.hashCode();
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      return new ReferenceType(referencedType.bindTypeVars(vals));
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
      if (concrete instanceof ReferenceType) {
        SwiftType concreteMember = ((ReferenceType)concrete).referencedType;
        return referencedType.matchTypeVars(concreteMember);
      }
      return null;
    }

    @Override
    public boolean hasTypeVar() {
      return referencedType.hasTypeVar();
    }
  }

  public static class StructType extends SwiftType {
    public static class StructField {
      private final SwiftType type;
      private final String name;
      public StructField(SwiftType type, String name) {
        this.type = type;
        this.name = name;
      }

      public SwiftType getType() {
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
    public StructureType getStructureType() {
      return StructureType.STRUCT;
    }

    public List<StructField> getFields() {
      return Collections.unmodifiableList(fields);
    }

    public int getFieldCount() {
      return fields.size();
    }


    public SwiftType getFieldTypeByName(String name) {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ReferenceType with " +
              "non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (!otherT.getStructureType().equals(StructureType.STRUCT)) {
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
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      // Assume no type variables inside struct
      return this;
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
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

  public static class ScalarValueType extends SwiftType {
    private final PrimType type;

    public ScalarValueType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.SCALAR_VALUE;
    }

    @Override
    public PrimType getPrimitiveType() {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ScalarValueType with "
            + "non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() != StructureType.SCALAR_VALUE) {
        return false;
      } else {
        return otherT.getPrimitiveType().equals(this.type);
      }
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarValueType.class.hashCode();
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      return this;
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
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


  public static class ScalarFutureType extends SwiftType {
    private final PrimType type;
    public ScalarFutureType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.SCALAR_FUTURE;
    }

    @Override
    public PrimType getPrimitiveType() {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ScalarFutureType with non-type "
            + "object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() == StructureType.SCALAR_FUTURE) {
        return otherT.getPrimitiveType() == this.type;
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarFutureType.class.hashCode();
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      return this;
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
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

  public static class ScalarUpdateableType extends SwiftType {
    private final PrimType type;
    public ScalarUpdateableType(PrimType type) {
      super();
      this.type = type;
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.SCALAR_UPDATEABLE;
    }

    @Override
    public PrimType getPrimitiveType() {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing ScalarUpdateableType " +
            "with non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() != StructureType.SCALAR_UPDATEABLE) {
        return false;
      } else {
        return otherT.getPrimitiveType().equals(this.type);
      }
    }

    public static ScalarFutureType asScalarFuture(SwiftType upType) {
      assert(Types.isScalarUpdateable(upType));
      return new ScalarFutureType(upType.getPrimitiveType());
    }
    
    public static ScalarValueType asScalarValue(SwiftType valType) {
      assert(Types.isScalarUpdateable(valType));
      return new ScalarValueType(valType.getPrimitiveType());
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ ScalarUpdateableType.class.hashCode();
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      return this;
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
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
  
  public static class UnionType extends SwiftType {
    private final List<SwiftType> alts;
    
    private UnionType(ArrayList<SwiftType> alts) {
      this.alts = Collections.unmodifiableList(alts);
    }

    public List<SwiftType> getAlternatives() {
      return alts;
    }
    
    public static List<SwiftType> getAlternatives(SwiftType type) {
      if (Types.isUnion(type)) {
        return ((UnionType)type).getAlternatives();
      } else {
        return Collections.singletonList(type);
      }
    }
    
    /**
     * @return UnionType if multiple types, or plain type if singular
     */
    public static SwiftType createUnionType(List<SwiftType> alts) {
      if (alts.size() == 1) {
        return alts.get(0);
      } else {
        return new UnionType(new ArrayList<SwiftType>(alts));
      }
    }
    
    public static SwiftType createUnionType(SwiftType ...alts) {
      if (alts.length == 1) {
        return alts[0];
      } else {
        ArrayList<SwiftType> list = new ArrayList<SwiftType>();
        for (SwiftType alt: alts) {
          list.add(alt);
        }
        return new UnionType(list);
      }
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.TYPE_UNION;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      sb.append("(");
      for (SwiftType alt: alts) {
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
      for (SwiftType alt: alts) {
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
      if (!(other instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing UnionType " +
            "with non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() != StructureType.TYPE_UNION) {
        return false;
      } else {
        UnionType otherUT = (UnionType)otherT;
        // Sets must be same size
        if (this.alts.size() != otherUT.alts.size()) {
          return false;
        }
        
        // Check members are the same
        for (SwiftType alt: this.alts) {
          if (!otherUT.alts.contains(alt)) {
            return false;
          }
        }
        return true;
      }
    }

    @Override
    public boolean assignableTo(SwiftType other) {
      if (Types.isUnion(other)) { 
        UnionType otherUnion = (UnionType)other;
        // Check if subset
        for (SwiftType alt: this.alts) {
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
      for (SwiftType alt: alts) {
        // Iteration order doesn't matter for xor
        hash ^= alt.hashCode();
      }
      return hash;
    }

    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      ArrayList<SwiftType> boundAlts = new ArrayList<SwiftType>(alts.size());
      for (SwiftType alt: alts) {
        boundAlts.add(alt.bindTypeVars(vals));
      }
      return new UnionType(boundAlts);
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
      throw new STCRuntimeError("Not yet implemented: matching typevars for"
          + " union types");
    }

    @Override
    public boolean hasTypeVar() {
      for (SwiftType alt: alts) {
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
  public static class TypeVariable extends SwiftType {
    private final String typeVarName;

    public TypeVariable(String typeVarName) {
      super();
      this.typeVarName = typeVarName;
    }

    @Override
    public StructureType getStructureType() {
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
    public String getTypeVarName() {
      return typeVarName;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing TypeVariable " +
            "with non-type object");
      }
      if (((SwiftType)obj).getStructureType() == StructureType.TYPE_VARIABLE) {
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
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      SwiftType binding = vals.get(typeVarName);
      if (binding == null) {
        return this;
      } else {
        return binding;
      }
    }

    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
      return Collections.singletonMap(typeVarName, concrete);
    }

    @Override
    public boolean hasTypeVar() {
      return true;
    }
  }
  
  private enum StructureType
  {
    SCALAR_UPDATEABLE,
    SCALAR_FUTURE,
    SCALAR_VALUE,
    ARRAY,
    // Reference is only used internally in compiler
    REFERENCE,
    STRUCT,
    TYPE_VARIABLE,
    TYPE_UNION,
    FUNCTION,
  }

  /**
   * Class to represent a complex swift type, which can be recursively
   * constructed out of scalar types, arrays, and structures.
   *
   */
  public abstract static class SwiftType {
    public abstract StructureType getStructureType();

    /**
     * Get the primitive type (only valid if scalar)
     * @return
     */
    public PrimType getPrimitiveType() {
      throw new STCRuntimeError("getPrimitiveType not implemented " +
      "for class " + getClass().getName());
    }

    public SwiftType getMemberType() {
      throw new STCRuntimeError("getMemberType not implemented " +
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
    public boolean assignableTo(SwiftType other) {
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
    public abstract SwiftType bindTypeVars(Map<String, SwiftType> vals);
    
    /**
     * Match up any typevars in this type to vars in a concrete type
     * and return the type var binding. Returns null if types can't be matched
     */
    public abstract Map<String, SwiftType> matchTypeVars(SwiftType concrete);
    
    /**
     * @return true if any type variables in type
     */
    public abstract boolean hasTypeVar();

    public String getTypeVarName() {
      throw new STCRuntimeError("getTypeVarName not supported for type "
                              + toString());
    }
  }

  /**
   * Function types are kept distinct from value types
   */
  public static class FunctionType extends SwiftType {

    private final ArrayList<SwiftType> inputs = new ArrayList<SwiftType>();
    private final ArrayList<SwiftType> outputs = new ArrayList<SwiftType>();
    private final ArrayList<String> typeVars = new ArrayList<String>();

    /** if varargs is true, the final argument can be repeated many times */
    private final boolean varargs;

    public FunctionType(List<SwiftType> inputs, List<SwiftType> outputs,
                                                boolean varargs) {
      this(inputs, outputs, varargs, null);
    }
    public FunctionType(List<SwiftType> inputs, List<SwiftType> outputs,
          boolean varargs, Collection<String> typeVars) {
      this.inputs.addAll(inputs);
      this.outputs.addAll(outputs);
      this.varargs = varargs;
      if (typeVars != null) {
        this.typeVars.addAll(typeVars);
        Collections.sort(this.typeVars);
      }
    }

    public List<SwiftType> getInputs() {
      return Collections.unmodifiableList(inputs);
    }

    public List<SwiftType> getOutputs() {
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
      for (Iterator<SwiftType> it = inputs.iterator(); it.hasNext(); ) {
        SwiftType t = it.next();
        sb.append(t);
        if (it.hasNext())
          sb.append(',');
      }
      sb.append(") -> (");
      for (Iterator<SwiftType> it = outputs.iterator();
           it.hasNext(); ) {
        SwiftType t = it.next();
        sb.append(t);
        if (it.hasNext())
          sb.append(',');
      }
      sb.append(')');
      return sb.toString();
    }

    @Override
    public StructureType getStructureType() {
      return StructureType.FUNCTION;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SwiftType)) {
        throw new STCRuntimeError("Comparing FunctionType " +
            "with non-type object");
      }
      if (((SwiftType)obj).getStructureType() == StructureType.FUNCTION) {
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
      for (SwiftType t: inputs) {
        code ^= t.hashCode();
      }
      for (SwiftType t: outputs) {
        code ^= t.hashCode();
      }
      code ^= ((Boolean)varargs).hashCode();
      return code;
    }
    @Override
    public SwiftType bindTypeVars(Map<String, SwiftType> vals) {
      List<SwiftType> boundInputs = new ArrayList<SwiftType>();
      for (SwiftType input: inputs) {
        boundInputs.add(input.bindTypeVars(vals));
      }
      
      List<SwiftType> boundOutputs = new ArrayList<SwiftType>();
      for (SwiftType output: outputs) {
        boundOutputs.add(output.bindTypeVars(vals));
      }
      
      return new FunctionType(boundInputs, boundOutputs, varargs);
    }
    @Override
    public Map<String, SwiftType> matchTypeVars(SwiftType concrete) {
      throw new STCRuntimeError("Not yet implemented: matching typevars for"
          + " function types");
    }
    @Override
    public boolean hasTypeVar() {
      for (SwiftType input: inputs) {
        if (input.hasTypeVar()) {
          return true;
        }
      }
      for (SwiftType output: outputs) {
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
    private final ArrayList<SwiftType> types;

    public ExprType(List<SwiftType> types) {
      this.types = new ArrayList<SwiftType>(types);
    }

    public ExprType(SwiftType exprType) {
      this.types = new ArrayList<SwiftType>(1);
      this.types.add(exprType);
    }

    public List<SwiftType> getTypes() {
      return Collections.unmodifiableList(types);
    }
    
    public SwiftType get(int index) {
      return types.get(index);
    }

    public int elems() {
      return types.size();
    }
    
    public String toString() {
      return types.toString();
    }
  }
  public static Map<String, SwiftType> getBuiltInTypes() {
    return Collections.unmodifiableMap(nativeTypes);
  }

  /**
   * Convenience function to check if a type is an array
   * @param t
   * @return
   */
  public static boolean isArray(SwiftType t) {
    return t.getStructureType() == StructureType.ARRAY;
  }

  /**
   * Convenience function to check if a type is a reference to an array
   * @param t
   * @return
   */
  public static boolean isArrayRef(SwiftType t) {
    return t.getStructureType() == StructureType.REFERENCE &&
        t.getMemberType().getStructureType() == StructureType.ARRAY;
  }

  /**
   * Convenience function to get member type of array or array ref
   */
  public static SwiftType getArrayMemberType(SwiftType arrayT) {
    if (isArray(arrayT)) {
      return arrayT.getMemberType();
    } else if (isArrayRef(arrayT)) {
      return arrayT.getMemberType().getMemberType();
    } else {
      throw new STCRuntimeError("called getArrayMemberType on non-array"
          + " type " + arrayT.toString());
    }
  }

  
  public static boolean isFuture(SwiftType t) {
    return isScalarFuture(t) || isReference(t);
  }
  
  /**
   * Convenience function to check if a type is scalar
   * @param t
   * @return
   */
  public static boolean isScalarFuture(SwiftType t) {
    return t.getStructureType() == StructureType.SCALAR_FUTURE;
  }

  public static boolean isScalarValue(SwiftType t) {
    return t.getStructureType() == StructureType.SCALAR_VALUE;
  }
  
  public static boolean isScalarUpdateable(SwiftType t) {
    return t.getStructureType() == StructureType.SCALAR_UPDATEABLE;
  }

  public static boolean isReference(SwiftType t) {
    return t.getStructureType() == StructureType.REFERENCE;
  }

  public static boolean isStruct(SwiftType t) {
    return t.getStructureType() == StructureType.STRUCT;
  }
  
  public static boolean isStructRef(SwiftType t) {
    return t.getStructureType() == StructureType.REFERENCE 
                            && Types.isStruct(t.getMemberType());
  }
  
  public static boolean isFileRef(SwiftType t) {
    return t.getStructureType() == StructureType.REFERENCE
        && Types.isFile(t.getMemberType());
  }

  public static boolean isBool(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.BOOLEAN;
  }
  
  public static boolean isInt(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.INTEGER;
  }

  public static boolean isFloat(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.FLOAT;
  }

  public static boolean isString(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.STRING;
  }
  
  public static boolean isVoid(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.VOID;
  }

  public static boolean isFile(SwiftType t) {
    return isScalarFuture(t) && t.getPrimitiveType() == PrimType.FILE;
  }
  
  public static boolean isReferenceTo(SwiftType refType, SwiftType valType) {
    return isReference(refType) && refType.getMemberType().equals(valType);
  }

  public static boolean isUpdateableEquiv(SwiftType up, SwiftType future) {
    return isScalarFuture(future) && isScalarUpdateable(up) && 
                      future.getPrimitiveType() == up.getPrimitiveType();
  }
  
  public static SwiftType derefResultType(SwiftType future) {
    if (future.getStructureType() == StructureType.SCALAR_FUTURE
          || future.getStructureType() == StructureType.SCALAR_UPDATEABLE) {
      return new ScalarValueType(future.getPrimitiveType());
    } else if (future.getStructureType() == StructureType.REFERENCE) {
      return future.getMemberType();
    } else {
      throw new STCRuntimeError(future.toString() + " can't "
          + " be dereferenced");
    }
    
  }
  
  /**
   * Is it a type we can map to a file?
   */
  public static boolean isMappable(SwiftType t) {
    // We can only map files right now..
    return t.equals(FUTURE_FILE);
  }

  public static boolean isUnion(SwiftType type) {
    return type.getStructureType() == StructureType.TYPE_UNION;
  }
  
  public static boolean isTypeVar(SwiftType type) {
    return type.getStructureType() == StructureType.TYPE_VARIABLE;
  }

  public static boolean isPolymorphic(SwiftType type) {
    return type.getStructureType() == StructureType.TYPE_UNION ||
        type.getStructureType() == StructureType.TYPE_VARIABLE;
  }
  
  public static boolean isFunction(SwiftType type) {
    return type.getStructureType() == StructureType.FUNCTION;
  }
  
  public static boolean listsEqual(List<SwiftType> a, List<SwiftType> b) {
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

  public static final SwiftType FUTURE_INTEGER =
                      new ScalarFutureType(PrimType.INTEGER);
  public static final SwiftType VALUE_INTEGER =
      new ScalarValueType(PrimType.INTEGER);
  public static final SwiftType REFERENCE_INTEGER =
        new ReferenceType(FUTURE_INTEGER);

  public static final SwiftType FUTURE_STRING =
                      new ScalarFutureType(PrimType.STRING);
  public static final SwiftType VALUE_STRING =
                      new ScalarValueType(PrimType.STRING);
  public static final SwiftType REFERENCE_STRING =
        new ReferenceType(FUTURE_STRING);

  public static final SwiftType FUTURE_FLOAT =
        new ScalarFutureType(PrimType.FLOAT);
  public static final SwiftType VALUE_FLOAT =
        new ScalarValueType(PrimType.FLOAT);
  public static final SwiftType REFERENCE_FLOAT =
        new ReferenceType(FUTURE_FLOAT);
  public static final SwiftType UPDATEABLE_FLOAT =
        new ScalarUpdateableType(PrimType.FLOAT);

  public static final SwiftType FUTURE_BOOLEAN =
        new ScalarFutureType(PrimType.BOOLEAN);
  public static final SwiftType VALUE_BOOLEAN =
        new ScalarValueType(PrimType.BOOLEAN);
  public static final SwiftType REFERENCE_BOOLEAN =
        new ReferenceType(FUTURE_BOOLEAN);
  
  public static final SwiftType FUTURE_BLOB =
      new ScalarFutureType(PrimType.BLOB);
  public static final SwiftType REFERENCE_BLOB =
      new ReferenceType(FUTURE_BLOB);
  public static final SwiftType VALUE_BLOB =
      new ScalarValueType(PrimType.BLOB);
  
  public static final SwiftType FUTURE_FILE =
      new ScalarFutureType(PrimType.FILE);
  public static final SwiftType REFERENCE_FILE =
      new ReferenceType(FUTURE_FILE);
  
  public static final SwiftType VALUE_VOID =
      new ScalarValueType(PrimType.VOID);
  public static final SwiftType FUTURE_VOID =
      new ScalarFutureType(PrimType.VOID);
  public static final SwiftType REFERENCE_VOID =
      new ReferenceType(FUTURE_VOID);
  
  private static final String VALUE_SIGIL = "$";

  private static final Map<String, SwiftType> nativeTypes;

  static {
    nativeTypes = new HashMap<String, SwiftType>();
    nativeTypes.put("int", Types.FUTURE_INTEGER);
    nativeTypes.put("string", Types.FUTURE_STRING);
    nativeTypes.put("float", Types.FUTURE_FLOAT);
    nativeTypes.put("boolean", Types.FUTURE_BOOLEAN);
    nativeTypes.put("void", Types.FUTURE_VOID);
    nativeTypes.put("blob", Types.FUTURE_BLOB);
    nativeTypes.put("file", Types.FUTURE_FILE);
    nativeTypes.put("updateable_float", Types.UPDATEABLE_FLOAT);
  }

}
