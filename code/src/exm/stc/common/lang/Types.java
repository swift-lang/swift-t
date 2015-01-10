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
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Types.StructType.StructField;
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
    private final boolean local; // If stored locally
    private final Type keyType;
    private final Type memberType;

    public ArrayType(boolean local, Type keyType, Type memberType) {
      this.local = local;
      this.keyType = keyType;
      this.memberType = memberType;
    }

    public static ArrayType sharedArray(Type keyType, Type memberType) {
      return new ArrayType(false, keyType, memberType);
    }

    public static ArrayType localArray(Type keyType, Type memberType) {
      return new ArrayType(true, keyType, memberType);
    }

    @Override
    public StructureType structureType() {
      if (local) {
        return StructureType.ARRAY_LOCAL;
      } else {
        return StructureType.ARRAY;
      }
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
      if (local) {
        return memberType.toString() + "$[" + keyType + "]";
      } else {
        return memberType.toString() + "[" + keyType + "]";
      }
    }

    @Override
    public String typeName() {
      if (local) {
        return memberType.typeName() + "$["  + keyType + "]";
      } else {
        return memberType.typeName() + "["  + keyType + "]";
      }
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
      return otherT.local == this.local &&
              otherT.memberType.equals(memberType) &&
              otherT.keyType.equals(keyType);
    }

    @Override
    public int hashCode() {
      return memberType.hashCode() + 13 *
            (ArrayType.class.hashCode() + 13 * (local ? 0 : 1));
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new ArrayType(local, keyType.bindTypeVars(vals),
                           memberType.bindTypeVars(vals));
    }

    @Override
    public Type bindAllTypeVars(Type type) {
      return new ArrayType(local, keyType.bindAllTypeVars(type),
          memberType.bindAllTypeVars(type));
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if ((!local && isArray(concrete)) ||
           (local && isArrayLocal(concrete))) {
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
      assert((isArray(this) && isArray(concrete)) ||
              (isArrayLocal(this) && isArrayLocal(concrete)));
      ArrayType concreteArray = (ArrayType)concrete.baseType();
      Type cMember = memberType.concretize(concreteArray.memberType());
      Type cKey = keyType.concretize(concreteArray.keyType);
      if (cMember == this.memberType && cKey == this.keyType)
        return this;
      return new ArrayType(this.local, cKey, cMember);
    }

    @Override
    public boolean assignableTo(Type other) {
      if (!isArray(other) && !isArrayLocal(other)) {
        return false;
      }
      ArrayType otherA = (ArrayType)other.baseType();
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
      if (implMember == memberType && implKey == keyType) {
        return this;
      } else {
        return new ArrayType(local, implKey, implMember);
      }
    }

    @Override
    public boolean isConcrete() {
      return keyType.isConcrete() && memberType.isConcrete();
    }

    public Type substituteElemType(Type newElem) {
      return new ArrayType(local, keyType, newElem);
    }
  }

  /**
   * Unordered set of data which allows duplicates
   */
  public static class BagType extends Type {

    private final boolean local; // If stored locally
    public static final String BAG = "bag";
    private final Type elemType;

    public BagType(boolean local, Type elemType) {
      this.local = local;
      this.elemType = elemType;
    }

    public static BagType sharedBag(Type memberType) {
      return new BagType(false, memberType);
    }

    public static BagType localBag(Type memberType) {
      return new BagType(true, memberType);
    }

    @Override
    public StructureType structureType() {
      if (local) {
        return StructureType.BAG_LOCAL;
      } else {
        return StructureType.BAG;
      }
    }

    @Override
    public Type memberType() {
      return elemType;
    }


    @Override
    public String toString() {
      if (local) {
        return BAG + "$<" + elemType.toString() + ">";
      } else {
        return BAG + "<" + elemType.toString() + ">";
      }
    }

    @Override
    public String typeName() {
      if (local) {
        return BAG + "$<" + elemType.typeName() + ">";
      } else {
        return BAG + "<" + elemType.typeName() + ">";
      }
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing BagType with non-type " +
                "object");
      }
      if (!(other instanceof BagType)) {
        return false;
      }
      BagType otherT = (BagType) other;
      return this.local == otherT.local &&
              otherT.elemType.equals(elemType);
    }

    @Override
    public int hashCode() {
      return local ? 0 : 1 + 31 *
              (elemType.hashCode() + 31 * BagType.class.hashCode());
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new BagType(local, elemType.bindTypeVars(vals));
    }

    @Override
    public Type bindAllTypeVars(Type type) {
      return new BagType(local, elemType.bindAllTypeVars(type));
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if ((!local && isBag(concrete))||
           (local && isBagLocal(concrete))   ) {
        BagType concreteBag = (BagType)concrete.baseType();
        return elemType.matchTypeVars(concreteBag.elemType);
      }
      return null;
    }

    @Override
    public Type concretize(Type concrete) {
      assert((!local && isBag(concrete)) ||
              (local && isBagLocal(concrete)));
      BagType concreteBag = (BagType)concrete.baseType();
      Type cElem = elemType.concretize(concreteBag.memberType());
      if (cElem == this.elemType)
        return this;
      return new BagType(local, cElem);
    }

    @Override
    public boolean assignableTo(Type other) {
      if (!isBag(other) && !isBagLocal(other)) {
        return false;
      }
      BagType otherB = (BagType)other.baseType();
      // For now, types must exactly match, due to contra/co-variance issues
      // with type parameters. Need to check to see if member types
      // can be converted to other member types

      return elemType.matchTypeVars(otherB.elemType) != null;
    }

    @Override
    public boolean hasTypeVar() {
      return elemType.hasTypeVar();
    }

    @Override
    public Type getImplType() {
      Type implElem = elemType.getImplType();
      if (implElem == elemType)
        return this;
      else
        return new BagType(local, implElem);
    }

    @Override
    public boolean isConcrete() {
      return elemType.isConcrete();
    }

    public Type substituteElemType(Type newElem) {
      return new BagType(local, newElem);
    }
  }

  public static class RefType extends Type {
    private final Type referencedType;
    private final boolean mutable;

    public RefType(Type referencedType, boolean mutable) {
      this.referencedType = referencedType;
      this.mutable = mutable;
    }

    public boolean mutable() {
      return mutable;
    }

    @Override
    public StructureType structureType() {
      if (mutable) {
        return StructureType.MUTABLE_REFERENCE;
      } else {
        return StructureType.CONST_REFERENCE;
      }
    }
    @Override
    public Type memberType() {
      return referencedType;
    }

    private String refSigil() {
      if (mutable) {
        return "*rw";
      } else {
        return "*r";
      }
    }

    @Override
    public String toString() {
      return refSigil() + "(" + referencedType.toString() + ")";
    }

    @Override
    public String typeName() {
      return refSigil() + "(" + referencedType.typeName() + ")";
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
    public boolean assignableTo(Type otherT) {
      if (!(otherT instanceof RefType)) {
        return false;
      }

      RefType otherRef = (RefType)otherT;
      /* TODO: currently treat mutable and non-mutable as separate types
      if (otherRef.mutable && !this.mutable) {
        return false;
      }*/
      return referencedType.assignableTo(otherRef.referencedType);
    }


    @Override
    public int hashCode() {
      return (referencedType.hashCode() * 13 + RefType.class.hashCode()) +
              (mutable ? 1 : 0);
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      return new RefType(referencedType.bindTypeVars(vals), mutable);
    }


    @Override
    public Type bindAllTypeVars(Type type) {
      return new RefType(referencedType.bindAllTypeVars(type), mutable);
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
      return new RefType(cMember, mutable);
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
        return new RefType(implMember, mutable);
    }

    @Override
    public boolean isConcrete() {
      return referencedType.isConcrete();
    }
  }

  public static class StructType extends Type {
    public static class StructField implements Typed {
      private final Type type;
      private final String name;
      public StructField(Type type, String name) {
        this.type = type;
        this.name = name;
      }

      @Override
      public Type type() {
        return type;
      }

      public String name() {
        return name;
      }

      @Override
      public String toString() {
        return name + ": " + type.typeName();
      }
    }

    public StructType(boolean local, String typeName,
                       List<StructField> fields) {
      this.local = local;
      this.typeName = typeName;
      this.fields = new ArrayList<StructField>(fields);
      this.hashCode = calcHashCode();
    }

    private final boolean local;
    private final List<StructField> fields;
    private final String typeName;

    private final int hashCode;

    public static StructType localStruct(StructType structType) {
      if (structType.local) {
        return structType;
      } else {
        return new StructType(true, structType.typeName, structType.fields);
      }
    }

    public static StructType sharedStruct(StructType structType) {
      if (structType.local) {
        return new StructType(false, structType.typeName, structType.fields);
      } else {
        return structType;
      }
    }

    public static StructType localStruct(String typeName,
                                   List<StructField> fields) {
      return new StructType(true, typeName, fields);
    }

    public static StructType sharedStruct(String typeName,
                                  List<StructField> fields) {
      return new StructType(false, typeName, fields);
    }

    /**
     * @return struct type name without any sigils
     */
    public String getStructTypeName() {
      return typeName;
    }

    public boolean isLocal() {
      return local;
    }

    @Override
    public StructureType structureType() {
      return local ? StructureType.STRUCT_LOCAL : StructureType.STRUCT;
    }

    public List<StructField> fields() {
      return Collections.unmodifiableList(fields);
    }

    public int fieldCount() {
      return fields.size();
    }


    public Type fieldTypeByName(String name) {
      for (StructField field: fields) {
        if (field.name().equals(name)) {
          return field.type();
        }
      }
      return null;
    }

    /**
     * Follow field path through one or more levels of structs to
     * find type
     * @param fields
     * @return
     * @throw {@link TypeMismatchException} if path invalid
     */
    public Type fieldTypeByPath(List<String> fields)
        throws TypeMismatchException {
      Type curr = this;
      for (String field: fields) {
        curr = curr.getImplType();
        if (!(curr instanceof StructType)) {
          throw new TypeMismatchException("Can't lookup field " + field +
                                          " in non-struct type: " + curr);
        }
        Type fieldType = ((StructType)curr).fieldTypeByName(field);
        if (fieldType == null) {
          throw new TypeMismatchException("Field " + field + " does not "
                                      + " exist in struct type " + curr);
        }
        curr = fieldType;
      }
      return curr;
    }

    public int fieldIndexByName(String name) {
      for (int i=0; i < fields.size(); i++) {
        StructField field = fields.get(i);
        if (field.name().equals(name)) {
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
      if (!isStruct(otherT) && !isStructLocal(otherT)) {
        return false;
      } else {
        // Type names should match, along with fields
        StructType otherST = (StructType)otherT;
        if (otherST.local != this.local ||
                !otherST.getStructTypeName().equals(typeName)) {
          return false;
        } else {
          // Names match, now check that fields match.
          if (otherST.fields.size() != fields.size()) {
            return false;
          }
          for (int i = 0; i < fields.size(); i++) {
            StructField f1 = fields.get(i), f2 = otherST.fields.get(i);
            if (!f1.name.equals(f2.name) || !f1.type.equals(f2.type)) {
              return false;
            }
          }
          return true;
        }
      }
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      if (local) {
        s.append(VALUE_SIGIL);
      }
      s.append("struct " + this.typeName + " {");
      boolean first = true;
      for (StructField f: fields) {
        if (first) {
          first = false;
        } else {
          s.append("; ");
        }
        s.append(f.type().toString());
        s.append(' ');
        s.append(f.name());
      }
      s.append("}");
      return s.toString();
    }

    @Override
    public String typeName() {
      if (local) {
        return VALUE_SIGIL + this.typeName;
      } else {
        return this.typeName;
      }
    }

    @Override
    public int hashCode() {
      // Use cached hashcode
      return hashCode;
    }

    private int calcHashCode() {
      int code = ((StructType.class.hashCode() * 13) +
               typeName.hashCode()) * 2 + (local ? 0 : 1);

     for (StructField field: fields) {
       code *= 13;
       code += (field.name.hashCode() * 7) + field.type.hashCode();
     }

      return code;
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      // Assume no type variables inside struct
      return this;
    }

    @Override
    public Type bindAllTypeVars(Type type) {
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

    @Override
    public boolean isConcrete() {
      // Concrete if all fields are concrete (they probably should be..)
      for (StructField f: fields) {
        if (!f.type().isConcrete()) {
          return false;
        }
      }
      return true;
    }

    /**
     * @return true if this or a nested struct has a ref field
     */
    public boolean hasRefField() {
      for (StructField f: this.fields) {
        Type fType = f.type();
        if (Types.isRef(fType)) {
          return true;
        } else if (Types.isStruct(fType) ||
                   Types.isStructLocal(fType)) {
          if (((StructType)fType.getImplType()).hasRefField()) {
            return true;
          }
        }
      }

      return false;
    }
  }


  /**
   * Enum to represent the primitive types implemented in the language.
   */
  public static enum PrimType {
    INT, STRING, FLOAT, BOOL, BLOB,
     // Void is a type with no information (i.e. the external type in Swift/K,
     // or the unit type in functional languages)
    VOID,
    // Files are treated in a similar way to other primitive types in Swift
    FILE;

    public String typeName() {
      switch (this) {
        case INT:
          return "int";
        case STRING:
          return "string";
        case FLOAT:
          return "float";
        case BOOL:
          return "boolean";
        case VOID:
          return "void";
        case BLOB:
          return "blob";
        default:
          throw new STCRuntimeError("typeName not implemented for " + this);
      }
    }
  }

  /**
   * Abstract class with common functionality for primitive types
   */
  public static abstract class AbstractPrimType extends Type {

    @Override
    public abstract PrimType primType();

    @Override
    public Type getImplType() {
      // This is a primitive type: implements itself
      return this;
    }

    @Override
    public boolean isConcrete() {
      return true;
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      // No type vars in primitive
      return this;
    }

    @Override
    public Type bindAllTypeVars(Type type) {
      // No type vars in primitive
      return this;
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      concrete = concrete.stripSubTypes();
      // No type vars in primitive, but check that types match
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
    public String toString() {
      // Just return plain typename
      return this.typeName();
    }
  }

  /**
   * Abstract class with common functionality for scalar types
   */
  public static abstract class AbstractScalarType extends AbstractPrimType {
    protected final PrimType primType;

    public AbstractScalarType(PrimType primType) {
      assert(primType != PrimType.FILE); // File type is handled elsewhere
      this.primType = primType;
    }

    @Override
    public PrimType primType() {
      return this.primType;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing " + this.getClass().getName() +
                                  " with non-type object");
      }
      Type otherT = (Type) other;
      // Check that class and primType match
      if (this.getClass().equals(other.getClass())) {
        return ((AbstractScalarType)otherT).primType == this.primType;
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return primType.hashCode() * 31 + getClass().hashCode();
    }


  }

  public static class ScalarValueType extends AbstractScalarType {

    public ScalarValueType(PrimType type) {
      super(type);
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_VALUE;
    }

    @Override
    public String typeName() {
      return VALUE_SIGIL + primType.typeName();
    }
  }


  public static class ScalarFutureType extends AbstractScalarType {
    public ScalarFutureType(PrimType primType) {
      super(primType);
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_FUTURE;
    }

    @Override
    public String typeName() {
      return primType.typeName();
    }
  }

  public static class ScalarUpdateableType extends AbstractScalarType {
    public ScalarUpdateableType(PrimType primType) {
      super(primType);
    }

    @Override
    public StructureType structureType() {
      return StructureType.SCALAR_UPDATEABLE;
    }

    @Override
    public String typeName() {
      return "updateable_" + primType.typeName();
    }

    public static ScalarFutureType asScalarFuture(Type upType) {
      assert(upType instanceof ScalarUpdateableType);
      return new ScalarFutureType(upType.primType());
    }

    public static ScalarValueType asScalarValue(Type valType) {
      assert(valType instanceof ScalarUpdateableType);
      return new ScalarValueType(valType.primType());
    }

  }

  public static enum FileKind {
    LOCAL_FS, // A file on the local file system
    URL // A file represented by a URL
    ;

    public String typeName() {
      switch (this) {
        case LOCAL_FS:
          return "file";
        case URL:
          return "url";
        default:
          throw new STCRuntimeError("typeName not implemented for " + this);
      }
    }

    /**
     * @return Whether this file kind supports creation of temporary files
     */
    public boolean supportsTmpImmediate() {
      switch (this) {
        case LOCAL_FS:
          return true;
        case URL:
          return false;
        default:
          throw new STCRuntimeError("not implemented for " + this);
      }
    }

    /**
     * @return Whether we can do a physical copy from/to two different
     * paths of this file type
     */
    public boolean supportsPhysicalCopy() {
      switch (this) {
        case LOCAL_FS:
          return true;
        case URL:
          return false;
        default:
          throw new STCRuntimeError("not implemented for " + this);
      }
    }
  }

  /**
   * A file variable.  There are multiple possible types of files, such as
   * those on a local file system, or those represented by a URL
   */
  public abstract static class AbstractFileType extends AbstractPrimType {

    protected final FileKind kind;

    private AbstractFileType(FileKind kind) {
      this.kind = kind;
    }

    @Override
    public PrimType primType() {
      return PrimType.FILE;
    }

    @Override
    public FileKind fileKind() {
      return kind;
    }

    @Override
    public int hashCode() {
      return kind.hashCode() + 13 * getClass().hashCode();
    }

    @Override
    public boolean equals(Object other) {
      // Generic comparison algorithm for files
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing " + this.getClass().getName() +
                                  "with non-type object");
      }
      Type otherT = (Type) other;
      if (this.getClass().isInstance(otherT)) {
        // Check that the kind matches
        return ((AbstractFileType)otherT).kind == this.kind;
      } else {
        return false;
      }
    }
  }

  public static class FileValueType extends AbstractFileType {

    public FileValueType(FileKind kind) {
      super(kind);
    }

    @Override
    public StructureType structureType() {
      return StructureType.FILE_VALUE;
    }

    @Override
    public String typeName() {
      return VALUE_SIGIL + this.kind.typeName();
    }

  }

  public static class FileFutureType extends AbstractFileType {

    public FileFutureType(FileKind kind) {
      super(kind);
    }

    @Override
    public StructureType structureType() {
      return StructureType.FILE_FUTURE;
    }

    @Override
    public String typeName() {
      return this.kind.typeName();
    }

  }

  /**
   * A union of multiple types that represents situations such as:
   * - A function that accepts multiple alternative types for an input parameter
   * - An expression that can be evaluated to multiple possible types.
   */
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

    /**
     * Return the list of possible types in a union.  This can be called on
     * non-union types for convenience, in which case it will only return
     * the single non-union type.
     * @param type
     * @return
     */
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
    public Type bindAllTypeVars(Type type) {
      ArrayList<Type> boundAlts = new ArrayList<Type>(alts.size());
      for (Type alt: alts) {
        boundAlts.add(alt.bindAllTypeVars(type));
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
      ArrayList<Type> implAlts = new ArrayList<Type>();
      for (Type t: alts) {
        implAlts.add(t.getImplType());
      }
      return new UnionType(implAlts);
    }

    @Override
    public boolean isConcrete() {
      return false;
    }
  }

  /**
   * A type with multiple fields.
   */
  public static class TupleType extends Type {
    private final List<Type> fields;

    private TupleType(ArrayList<Type> alts) {
      // Shouldn't have single-element union type
      assert(alts.size() != 1);
      this.fields = Collections.unmodifiableList(alts);
    }

    public List<Type> getFields() {
      return fields;
    }

    public int numFields() {
      return fields.size();
    }

    public Type getField(int i) {
      return fields.get(i);
    }

    /**
     * Return the list of fields in tuple, or original type if not a tuple
     * @param type
     * @return
     */
    public static List<Type> getFields(Type type) {
      if (isTuple(type)) {
        return ((TupleType)type).getFields();
      } else {
        return Collections.singletonList(type);
      }
    }

    /**
     * @return TupleType if multiple fields, or plain type if singular
     */
    public static Type makeTuple(List<Type> fields) {
      if (fields.size() == 1) {
        return fields.get(0);
      } else {
        return new TupleType(new ArrayList<Type>(fields));
      }
    }

    public static Type makeTuple(Type ...fields) {
      return makeTuple(Arrays.asList(fields));
    }

    @Override
    public StructureType structureType() {
      return StructureType.TUPLE;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      sb.append("(");
      for (Type field: fields) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        sb.append(field.toString());
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    public String typeName() {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      sb.append("(");
      for (Type field: fields) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        sb.append(field.typeName());
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Type)) {
        throw new STCRuntimeError("Comparing TupleType " +
            "with non-type object");
      }
      Type otherT = (Type) other;
      if (otherT.structureType() != StructureType.TUPLE) {
        return false;
      } else {
        TupleType otherTT = (TupleType)otherT;
        // Sets must be same size
        if (this.fields.size() != otherTT.fields.size()) {
          return false;
        }

        // Check members are the same
        for (int i = 0; i < this.fields.size(); i++) {
          Type field1 = this.fields.get(i);
          Type field2 = otherTT.fields.get(i);
          if (!field1.equals(field2)) {
            return false;
          }
        }
        return true;
      }
    }

    @Override
    public boolean assignableTo(Type other) {
      if (isTuple(other)) {
        TupleType otherTT = (TupleType)other;
        // Check fields
        if (this.fields.size() != otherTT.fields.size()) {
          return false;
        }
        for (int i = 0; i < fields.size(); i++) {
          if (!this.fields.get(i).assignableTo(otherTT.fields.get(i))) {
            return false;
          }
        }
        return true;
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      int hash = TupleType.class.hashCode();
      for (Type field: fields) {
        hash = hash * 13 + field.hashCode();
      }
      return hash;
    }

    @Override
    public Type bindTypeVars(Map<String, Type> vals) {
      ArrayList<Type> boundFields = new ArrayList<Type>(fields.size());
      for (Type field: fields) {
        boundFields.add(field.bindTypeVars(vals));
      }
      return new TupleType(boundFields);
    }

    @Override
    public Type bindAllTypeVars(Type type) {
      ArrayList<Type> boundFields = new ArrayList<Type>(fields.size());
      for (Type field: fields) {
        boundFields.add(field.bindAllTypeVars(type));
      }
      return new TupleType(boundFields);
    }

    @Override
    public Map<String, Type> matchTypeVars(Type concrete) {
      if (!Types.isTuple(concrete)) {
        return null;
      }
      TupleType concreteTT = (TupleType)concrete;
      if (this.fields.size() != concreteTT.fields.size()) {
        return null;
      }

      Map<String, Type> tvs = new HashMap<String, Type>();

      for (int i = 0; i < this.fields.size(); i++) {
        Type field = this.fields.get(i);
        Type concreteField = concreteTT.fields.get(i);

        Map<String, Type> fieldTVs = field.matchTypeVars(concreteField);
        if (fieldTVs == null) {
          return null;
        }
        tvs.putAll(fieldTVs);
      }

      return tvs;
    }

    @Override
    public Type concretize(Type concrete) {
      assert(this.assignableTo(concrete));
      assert(Types.isTuple(concrete));
      TupleType concreteTT = (TupleType)concrete;
      assert(concreteTT.fields.size() == this.fields.size());
      boolean differences = false;

      // Assume could be alt types etc in fields
      List<Type> concreteFields = new ArrayList<Type>(this.fields.size());
      for (int i = 0; i < fields.size(); i++) {
        Type field = this.fields.get(i);
        Type concreteField = field.concretize(concreteTT.fields.get(i));
        if (concreteField != field) {
          differences = true;
        }
        concreteFields.add(concreteField);
      }

      // Don't create new type unless needed
      return differences ? TupleType.makeTuple(concreteFields) : this;
    }

    @Override
    public boolean hasTypeVar() {
      for (Type field: fields) {
        if (field.hasTypeVar()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Type getImplType() {
      boolean differences = false;
      ArrayList<Type> implFields = new ArrayList<Type>(this.fields.size());
      for (Type field: fields) {
        Type fieldImpl = field.getImplType();
        implFields.add(fieldImpl);
        if (fieldImpl != field) {
          differences = true;
        }
      }

      // Avoid creating identical type objects
      return differences ? new TupleType(implFields) : this;
    }

    @Override
    public boolean isConcrete() {
      for (Type field: fields) {
        if (!field.isConcrete()) {
          return false;
        }
      }
      return true;
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
    public Type bindAllTypeVars(Type type) {
      // Bind wildcard to type
      return type;
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
      return this;
    }

    @Override
    public boolean isConcrete() {
      return false;
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
    public Type bindAllTypeVars(Type type) {
      // Bind wildcard to type
      return type;
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
      return this;
    }

    @Override
    public boolean isConcrete() {
      return false;
    }
  }

  private enum StructureType
  {
    SCALAR_UPDATEABLE,
    SCALAR_FUTURE,
    SCALAR_VALUE,
    FILE_FUTURE,
    FILE_VALUE,
    ARRAY, ARRAY_LOCAL,
    BAG, BAG_LOCAL,
    /** Reference types are only used internally in compiler */
    MUTABLE_REFERENCE, CONST_REFERENCE,
    STRUCT, STRUCT_LOCAL,
    TYPE_VARIABLE,
    WILDCARD,
    TYPE_UNION,
    TUPLE,
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
      throw new STCRuntimeError("primType() not implemented " +
      "for class " + getClass().getName());
    }

    public FileKind fileKind() {
      throw new STCRuntimeError("fileKind() not implemented " +
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
     * Bind any type variables to specified type
     * @param type
     * @return
     */
    public abstract Type bindAllTypeVars(Type type);

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
     *        This will find the implementation type of any parameter types.
     *        If not a concrete type, e.g. type var, return original type
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
    public abstract boolean isConcrete();

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

    public List<Type> asList() {
      return Collections.singletonList(this);
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
      // TODO: canonical way to show function type?
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
    public Type bindAllTypeVars(Type type) {
      List<Type> boundInputs = new ArrayList<Type>();
      for (Type input: inputs) {
        boundInputs.add(input.bindAllTypeVars(type));
      }

      List<Type> boundOutputs = new ArrayList<Type>();
      for (Type output: outputs) {
        boundOutputs.add(output.bindAllTypeVars(type));
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
      assert(isFunction(concrete));
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
        ins.add(in.getImplType());
      }

      for (Type out: outputs) {
        outs.add(out.getImplType());
      }

      return new FunctionType(ins, outs, varargs);
    }

    @Override
    public boolean isConcrete() {
      // Should be able to instantiate function in principle
      return true;
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
    public FileKind fileKind() {
      return baseType.fileKind();
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
    public Type bindAllTypeVars(Type type) {
      return new SubType(baseType.bindAllTypeVars(type), name);
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
    @Override
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

    @Override
    public boolean isConcrete() {
      return baseType.isConcrete();
    }

    @Override
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

  public static boolean isArrayLocal(Typed t) {
    return t.type().structureType() == StructureType.ARRAY_LOCAL;
  }

  /**
   * Convenience function to check if a type is a reference to an array
   * @param t
   * @return
   */
  public static boolean isArrayRef(Typed t) {
    return isRef(t) && isArray(t.type().memberType());
  }

  public static boolean isArrayRef(Typed t, boolean mutable) {
    return isRef(t, mutable) && isArray(t.type().memberType());
  }

  public static boolean isArrayLocalRef(Typed t) {
    return isRef(t) && isArrayLocal(t.type().memberType());
  }

  public static boolean isBag(Typed t) {
    return t.type().structureType() == StructureType.BAG;
  }

  public static boolean isBagLocal(Typed t) {
    return t.type().structureType() == StructureType.BAG_LOCAL;
  }

  public static boolean isBagRef(Typed t) {
    return isRef(t) && isBag(t.type().memberType());
  }

  public static boolean isBagRef(Typed t, boolean mutable) {
    return isRef(t, mutable) && isBag(t.type().memberType());
  }

  public static boolean isBagLocalRef(Typed t) {
    return isRef(t) && isBagLocal(t.type().memberType());
  }

  /**
   * @return whether it is a shared container data structure
   *          e.g. an array or multiset
   */
  public static boolean isContainer(Typed t) {
    return isArray(t) || isBag(t);
  }

  public static boolean isContainerLocal(Typed t) {
    return isArrayLocal(t) || isBagLocal(t);
  }

  public static boolean isContainerRef(Typed t) {
    return isArrayRef(t) || isBagRef(t);
  }

  public static boolean isContainerLocalRef(Typed t) {
    return isArrayLocalRef(t) || isBagLocalRef(t);
  }

  /**
   * Convenience function to get member type of array or array ref
   */
  public static Type containerElemType(Typed t) {
    if (isArray(t) || isArrayLocal(t)) {
      return t.type().memberType();
    } else if (isArrayRef(t) || isArrayLocalRef(t)) {
      return t.type().memberType().memberType();
    } else if (isBag(t) || isBagLocal(t)) {
      return t.type().memberType();
    } else if (isBagRef(t) || isBagLocalRef(t)) {
      return t.type().memberType().memberType();
    } else {
      throw new STCRuntimeError("called containerElemType on non-container"
          + " type " + t.toString());
    }
  }

  public static Type containerElemValType(Typed t) {
    return retrievedType(containerElemType(t));
  }

  public static boolean isElemType(Typed cont, Typed elem) {
    Type expected = containerElemType(cont.type());
    return (elem.type().assignableTo(expected));
  }

  public static boolean isElemValType(Typed cont, Typed elem) {
    Type expected = containerElemValType(cont.type());
    return (elem.type().assignableTo(expected));
  }

  public static Type substituteElemType(Typed cont, Type newElem) {
    boolean isRef = isRef(cont);
    boolean isMutableRef = isMutableRef(cont);
    if (isRef) {
      cont = cont.type().memberType();
    }

    Type newCont;
    if (isArray(cont) || isArrayLocal(cont)) {
      newCont = ((ArrayType)cont).substituteElemType(newElem);
    } else {
      assert(isBag(cont) || isBagLocal(cont));
      newCont = ((BagType)cont).substituteElemType(newElem);
    }

    if (isRef) {
      return new RefType(newCont, isMutableRef);
    } else {
      return newCont;
    }
  }

  public static Type arrayKeyType(Typed arr) {
    if (isArray(arr) || isArrayLocal(arr)) {
      return ((ArrayType)arr.type().baseType()).keyType;
    } else {
      assert(isArrayRef(arr)) : arr.type();
      return ((ArrayType)arr.type().baseType().memberType()).keyType;
    }
  }

  public static boolean isArrayKeyVal(Typed arr, Arg key) {
    // Interpret arg type as a value
    Type actual = key.typeInternal(false);
    // Get the value type of the array key
    Type expected = retrievedType(arrayKeyType(arr));
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

  public static boolean isStructField(Typed struct,
            List<String> fieldPath, Typed field) {
    Type fieldType;
    try {
      fieldType = structFieldType(struct, fieldPath);
    } catch (TypeMismatchException e) {
      return false;
    }
    return field.type().assignableTo(fieldType);
  }

  /**
   *
   * @param struct
   * @param fieldPath
   * @throw TypeMismatchException if not a field
   * @return
   * @throws TypeMismatchException
   */
  public static Type structFieldType(Typed struct, List<String> fieldPath) throws TypeMismatchException {
    assert(Types.isStruct(struct) || Types.isStructRef(struct) ||
            isStructLocal(struct));
    StructType structType;
    if (isStruct(struct) || isStructLocal(struct)) {
      structType = (StructType)struct.type().getImplType();
    } else {
      structType = (StructType)struct.type().getImplType().memberType();
    }
    return structType.fieldTypeByPath(fieldPath);
  }

  public static boolean isStructFieldVal(Typed struct,
      List<String> fieldPath, Typed field) {
    Type fieldType;
    try {
      fieldType = structFieldType(struct, fieldPath);
    } catch (TypeMismatchException e) {
      return false;
    }
    return field.type().assignableTo(retrievedType(fieldType));
  }

  /**
   * Return true if the type is one that we can subscribe to
   * the final value of
   * @param type
   * @return
   */
  public static boolean canWaitForFinalize(Typed type) {
    return isFuture(type) || isPrimUpdateable(type) ||
            isContainer(type) || isStruct(type);
  }

  /**
   * Check if the type is any kind of future that has single-assignment
   * semantics
   * @param t
   * @return
   */
  public static boolean isFuture(Typed t) {
    return isPrimFuture(t) || isRef(t);
  }


  /**
   * Convenience function to check if a type is a primitive future
   * @param t
   * @return
   */
  public static boolean isPrimFuture(Typed t) {
    return isFile(t) || isScalarFuture(t);
  }

  public static boolean isPrimValue(Typed t) {
    return isFileVal(t) || isScalarValue(t);
  }

  public static boolean isPrimUpdateable(Typed t) {
    return isScalarUpdateable(t);
  }

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
    return isConstRef(t) || isMutableRef(t);
  }

  public static boolean isRef(Typed t, boolean mutable) {
    return mutable ? isMutableRef(t): isConstRef(t);
  }

  public static boolean isConstRef(Typed t) {
    return t.type().structureType() == StructureType.CONST_REFERENCE;
  }

  public static boolean isMutableRef(Typed t) {
    return t.type().structureType() == StructureType.MUTABLE_REFERENCE;
  }

  public static boolean isStruct(Typed t) {
    return t.type().structureType() == StructureType.STRUCT;
  }

  public static boolean isStructLocal(Typed t) {
    return t.type().structureType() == StructureType.STRUCT_LOCAL;
  }

  public static boolean isStructRef(Typed t) {
    return isRef(t) && isStruct(t.type().memberType());
  }

  public static boolean isStructRef(Typed t, boolean mutable) {
    return isRef(t, mutable) && isStruct(t.type().memberType());
  }


  public static boolean isFuture(PrimType primType, Typed t) {
    return isPrimFuture(t) && t.type().primType() == primType;
  }

  public static boolean isVal(PrimType primType, Typed t) {
    return isPrimValue(t) && t.type().primType() == primType;
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
    return t.type().structureType() == StructureType.FILE_FUTURE;
  }

  public static boolean isFileVal(Typed t) {
    return t.type().structureType() == StructureType.FILE_VALUE;
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

  public static boolean isRefTo(Typed refType, Typed valType, boolean mutable) {
    return isRef(refType, mutable) &&
           refType.type().memberType().equals(valType.type());
  }

  public static boolean isAssignableRefTo(Typed refType, Typed valType) {
    return isRef(refType) &&
        refType.type().memberType().assignableTo(valType.type());
  }

  public static boolean isAssignableRefTo(Typed refType, Typed valType,
                                          boolean mutable) {
    return isRef(refType, mutable) &&
        refType.type().memberType().assignableTo(valType.type());
  }

  public static boolean isUpdateableEquiv(Typed up,
                                          Typed future) {
    return isScalarFuture(future) && isScalarUpdateable(up) &&
              future.type().primType() == up.type().primType();
  }

  /**
   * Check if we can dereference the type
   * @param t
   * @return
   */
  public static boolean isNonLocal(Typed t) {
    if (isContainer(t) || isRef(t) || isPrimFuture(t)) {
      return true;
    } else if (isStruct(t)) {
      return true;
    } else if (isPrimValue(t) || isContainerLocal(t) ||
               isStructLocal(t)) {
      return false;
    } else {
      throw new STCRuntimeError("Not sure if can deref " + t);
    }
  }

  public static boolean isNonLocalRef(Typed t, boolean mutable) {
    return isRef(t, mutable) && isNonLocal(t.type().memberType());
  }

  /**
   * The type that would result from a non-recursive retrieve operation
   * @param t
   * @return
   */
  public static Type retrievedType(Typed t) {
    return retrievedType(t, false);
  }

  /**
   * The type that would result from a retrieve operation
   * @param t
   * @return
   */
  public static Type retrievedType(Typed t, boolean recursive) {
    if (isScalarFuture(t) || isScalarUpdateable(t))  {
      return new ScalarValueType(t.type().primType());
    } else if (isFile(t)) {
      return new FileValueType(t.type().fileKind());
    } else if (isRef(t)) {
      return t.type().baseType().memberType();
    } else if (recursive &&
        (isContainer(t) || isContainerLocal(t))) {
      return unpackedType(t);
    } else if (isArray(t)) {
      assert(!recursive);
      ArrayType at = (ArrayType)t.type().getImplType();
      Type retrievedMemberType = retrievedType(at.memberType(), false);
      return ArrayType.localArray(at.keyType(), retrievedMemberType);
    } else if (isBag(t)) {
      assert(!recursive);
      BagType bt = (BagType)t.type().getImplType();
      Type retrievedMemberType = retrievedType(bt.memberType(), false);
      return BagType.localBag(retrievedMemberType);
    } else if (isStruct(t)) {
      StructType st = (StructType)t.type().getImplType();
      if (recursive) {
        return unpackedType(st);
      } else {
        return StructType.localStruct(st);
      }
    } else {
      throw new STCRuntimeError(t.type() + " can't be dereferenced");
    }
  }

  /**
   * Type that would result from storing this type
   * @param t
   * @param mutable if should be mutable
   * @return
   */
  public static Type storeResultType(Typed t, boolean mutable) {
    if (isScalarFuture(t) || isScalarUpdateable(t) ||
            isFile(t) || isRef(t) || isContainer(t) || isStruct(t))  {
      return new RefType(t.type(), mutable);
    } else if (isScalarValue(t)) {
      return new ScalarFutureType(t.type().primType());
    } else if (isFileVal(t)) {
      FileValueType fv = (FileValueType)t.type().getImplType();
      return new FileFutureType(fv.fileKind());
    } else if (isArrayLocal(t)) {
      ArrayType at = (ArrayType)t.type().getImplType();
      Type storedMemberType = storeResultType(at.memberType(), mutable);
      return ArrayType.sharedArray(at.keyType(), storedMemberType);
    } else if (isBagLocal(t)) {
      BagType bt = (BagType)t.type().getImplType();
      Type storedMemberType = storeResultType(bt.memberType(), mutable);
      return BagType.sharedBag(storedMemberType);
    } else if (isStructLocal(t)) {
      StructType st = (StructType)t.type().getImplType();
      return StructType.sharedStruct(st);
    } else {
      throw new STCRuntimeError(t.type() + " can't be stored");
    }
  }

  /**
   * Work out type of variable if we recursively extract all values into
   * local variables
   * @param t
   * @return
   */
  public static Type unpackedType(Typed t) {
    Type type = stripRefs(t.type());

    if (Types.isContainer(type) ||
        Types.isContainerLocal(type)) {
      // Recursively unpack
      return unpackedContainerType(type);
    } else if (Types.isStruct(type) || Types.isStructLocal(type)) {
      return unpackedStructType((StructType)type);
    } else {
      while (Types.isNonLocal(type)) {
        type = retrievedType(type);
      }
      return type;
    }
  }

  private static Type unpackedStructType(StructType structType) {
    List<StructField> packedFields = structType.fields();
    List<StructField> unpackedFields = new ArrayList<StructField>(packedFields.size());

    // Track whether the fields differ at all
    boolean differences = false;

    for (StructField packedField: packedFields) {
      Type packedFieldType = packedField.type();
      Type unpackedFieldType;

      if (Types.isRef(packedFieldType)) {
        // Follow references to unpack
        unpackedFieldType = unpackedType(packedFieldType);
      } else if (Types.isStruct(packedFieldType)) {
        // Nested struct
        unpackedFieldType = unpackedStructType((StructType)packedFieldType);
      } else {
        unpackedFieldType = packedFieldType;
      }

      if (unpackedFieldType.equals(packedFieldType)) {
        unpackedFields.add(packedField);
      } else {
        StructField unpackedField = new StructField(unpackedFieldType,
                                                    packedField.name());
        unpackedFields.add(unpackedField);
        differences = true;
      }
    }

    if (differences) {
      return new StructType(true, "unpacked:" + structType.typeName(),
                            unpackedFields);
    } else {
      return StructType.localStruct(structType);
    }
  }

  private static Type unpackedContainerType(Typed t) {
    assert(Types.isContainer(t) || Types.isContainerLocal(t));

    Type elemType = Types.containerElemType(t);

    // Recursively unpack
    Type elemValType = unpackedType(elemType);

    if (Types.isArray(t) || Types.isArrayLocal(t)) {
      ArrayType at = (ArrayType)t.type().getImplType();
      return ArrayType.localArray(at.keyType(), elemValType);
    } else {
      assert(Types.isBag(t) || Types.isBagLocal(t));
      return BagType.localBag(elemValType);
    }
  }

  private static Type stripRefs(Type t) {
    // Strip off references
    while (Types.isRef(t)) {
      t = Types.retrievedType(t);
    }

    return t;
  }

  /**
   * Is it a type we can map to a file?
   */
  public static boolean isMappable(Typed t) {
    // We can only map files right now..
    return isFile(t);
  }

  public static boolean isUnion(Typed type) {
    return type.type().structureType() == StructureType.TYPE_UNION;
  }

  public static boolean isTuple(Typed type) {
    return type.type().structureType() == StructureType.TUPLE;
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
   * @param input
   * @return
   */
  public static boolean inputRequiresInitialization(Var input) {
    return input.storage() == Alloc.ALIAS
        || isPrimUpdateable(input);
  }

  /**
   * Returns true if the variable requires initialiation before being used
   * in output context
   * @param output
   * @return
   */
  public static boolean outputRequiresInitialization(Var output) {
    return output.storage() == Alloc.ALIAS
        || isPrimUpdateable(output)
        || isFileVal(output);
  }

  /**
   * If the variable must be assigned before being used as input.
   * E.g. a non-future
   * @param var
   * @return
   */
  public static boolean assignBeforeRead(Var var) {
    return var.storage() == Alloc.LOCAL;
  }

  /**
   * More convenient way of representing array types for some analysies
   *
   */
  public static class NestedContainerInfo {
    public NestedContainerInfo(Typed baseType, int nesting) {
      super();
      this.baseType = baseType.type();
      this.nesting = nesting;
    }
    /**
     * Construct from regular SwiftType
     * @param type
     */
    public NestedContainerInfo(Typed type) {
      assert(isContainer(type) || isContainerRef(type));
      int depth = 0;
      while (isContainer(type) || isContainerRef(type)) {
        type = containerElemType(type);
        depth++;
      }
      this.nesting = depth;
      this.baseType = type.type();
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
      if (isWildcard(argType)) {
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

  /**
   * Remove degrees of freedom, e.g. wildcards and typevars
   * @param t
   * @return
   */
  public static Type concretiseArbitrarily(Type t) {
    // Make concrete
    t = UnionType.getAlternatives(t).get(0);
    t = t.bindAllTypeVars(Types.F_VOID);
    return t;
  }

  public static final Type F_INT = new ScalarFutureType(PrimType.INT);
  public static final Type V_INT = new ScalarValueType(PrimType.INT);

  public static final Type F_STRING = new ScalarFutureType(PrimType.STRING);
  public static final Type V_STRING = new ScalarValueType(PrimType.STRING);

  public static final Type F_FLOAT = new ScalarFutureType(PrimType.FLOAT);
  public static final Type V_FLOAT = new ScalarValueType(PrimType.FLOAT);
  public static final Type UP_FLOAT = new ScalarUpdateableType(PrimType.FLOAT);

  public static final Type F_BOOL = new ScalarFutureType(PrimType.BOOL);
  public static final Type V_BOOL = new ScalarValueType(PrimType.BOOL);

  public static final Type F_BLOB = new ScalarFutureType(PrimType.BLOB);
  public static final Type V_BLOB = new ScalarValueType(PrimType.BLOB);

  public static final Type F_FILE = new FileFutureType(FileKind.LOCAL_FS);
  public static final Type V_FILE = new FileValueType(FileKind.LOCAL_FS);

  public static final Type F_URL = new FileFutureType(FileKind.URL);
  public static final Type V_URL = new FileValueType(FileKind.URL);

  public static final Type V_VOID = new ScalarValueType(PrimType.VOID);
  public static final Type F_VOID = new ScalarFutureType(PrimType.VOID);

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
    registerPrimitiveType(F_URL);
    registerPrimitiveType(UP_FLOAT);
    registerPrimitiveType(F_LOCATION);
  }

  public static void registerPrimitiveType(Type type) {
    String name = type.typeName();
    assert(!nativeTypes.containsKey(name)): name;
    nativeTypes.put(name, type);
  }

}
