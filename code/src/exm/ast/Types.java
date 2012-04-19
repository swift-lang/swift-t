package exm.ast;

import java.util.*;

import exm.parser.util.ParserRuntimeException;

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
    public PrimType getPrimitiveType() {
      throw new ParserRuntimeException("getPrimitiveType not implemented " +
      "for arrays");
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
        throw new ParserRuntimeException("Comparing ArrayType with non-type " +
        		"object");
      }
      SwiftType otherT = (SwiftType) other;
      if (!otherT.getStructureType().equals(StructureType.ARRAY)) {
        return false;
      } else {
        return otherT.getMemberType().equals(memberType);
      }
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
    public PrimType getPrimitiveType() {
      throw new ParserRuntimeException("getPrimitiveType not implemented "
          + "for references");
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
        throw new ParserRuntimeException("Comparing ReferenceType with " +
              "non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (!otherT.getStructureType().equals(StructureType.REFERENCE)) {
        return false;
      } else {
        return otherT.getMemberType().equals(referencedType);
      }
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

    @Override
    public PrimType getPrimitiveType() {
      throw new ParserRuntimeException("getPrimitiveType not defined for "
          + " StructType");
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
        throw new ParserRuntimeException("Comparing ReferenceType with " +
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
        throw new ParserRuntimeException("Comparing ScalarValueType with "
            + "non-type object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() != StructureType.SCALAR_VALUE) {
        return false;
      } else {
        return otherT.getPrimitiveType().equals(this.type);
      }
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
        throw new ParserRuntimeException("Comparing ScalarFutureType with non-type "
            + "object");
      }
      SwiftType otherT = (SwiftType) other;
      if (otherT.getStructureType() != StructureType.SCALAR_FUTURE) {
        return false;
      } else {
        return otherT.getPrimitiveType().equals(this.type);
      }
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
        throw new ParserRuntimeException("Comparing ScalarUpdateableType " +
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
  }

  /**
   * Class to represent a complex swift type, which can be recursively
   * constructed out of scalar types, arrays, and structures.
   *
   */
  public abstract static class SwiftType
  {
    public abstract StructureType getStructureType();

    /**
     * Get the primitive type (only valid if scalar)
     * @return
     */
    public abstract PrimType getPrimitiveType();

    public SwiftType getMemberType() {
      throw new ParserRuntimeException("getMemberType not implemented " +
      "for this Type subclass");
    }

    /** Prints out a description of type for user */
    @Override
    public abstract String toString();

    /** Print out a short unique name for type */
    public abstract String typeName();
  }

  /**
   * Function types are kept distinct from value types
   */
  public static class FunctionType {

    /**
     * Have a special class for input arguments to allow representation of
     * polymorphic functions.  An input argument can be any of a list of
     * types.
     */
    public static class InArgT {
      private final SwiftType alternatives[];

      public InArgT(SwiftType... alternatives) {
        this.alternatives = alternatives;
      }


      public SwiftType[] getAlternatives() {
        return alternatives;
      }
      
      public SwiftType getAlt(int i) {
        return alternatives[i];
      }

      public static InArgT fromSwiftT(SwiftType t) {
        SwiftType alts[] = new SwiftType[1];
        alts[0] = t;
        return new InArgT(alts);
      }


      public static List<InArgT> convertList(List<SwiftType> inputs) {
        ArrayList<InArgT> result = new ArrayList<InArgT>(inputs.size());
        for (SwiftType t: inputs) {
          result.add(InArgT.fromSwiftT(t));
        }
        return result;
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (SwiftType alt: alternatives) {
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
      
      public String typeName() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (SwiftType alt: alternatives) {
          if (first) {
            first = false;
          } else {
            sb.append("|");
          }
          sb.append(alt.typeName());
        }
        return sb.toString();
      }


      public int getAltCount() {
        return alternatives.length;
      }
    }

    private final ArrayList<InArgT> inputs = new ArrayList<InArgT>();
    private final ArrayList<SwiftType> outputs = new ArrayList<SwiftType>();

    /** if varargs is true, the final argument can be repeated many times */
    private final boolean varargs;


    public FunctionType(List<InArgT> inputs, List<SwiftType> outputs) {
      this(inputs, outputs, false);
    }

    public FunctionType(List<InArgT> inputs, List<SwiftType> outputs,
                                                        boolean varargs) {
      this.inputs.addAll(inputs);
      this.outputs.addAll(outputs);
      this.varargs = varargs;
    }

    /**
     * Convenience function to convert from plain SwiftType to InArgT
     * only reason this is static method instead of constructor is to avoid
     * problem where two constructors have same erasure type
     * @param inputs
     * @param outputs
     */
    public static FunctionType create(List<SwiftType> inputs, List<SwiftType> outputs) {
      return new FunctionType(InArgT.convertList(inputs), outputs, false);
    }

    public List<InArgT> getInputs() {
      return Collections.unmodifiableList(inputs);
    }

    public List<SwiftType> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    public boolean hasVarargs() {
      return varargs;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(128);
      sb.append('(');
      for (Iterator<InArgT> it = inputs.iterator(); it.hasNext(); ) {
        InArgT t = it.next();
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
      throw new ParserRuntimeException("called getArrayMemberType on non-array"
          + " type " + arrayT.toString());
    }
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
      throw new ParserRuntimeException(future.toString() + " can't "
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
  
  /**
   * Do we need to map the file upon creation
   */
  public static boolean requiresMapping(SwiftType t) {
    // We can only map files right now..
    return t.equals(FUTURE_FILE);
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


}
