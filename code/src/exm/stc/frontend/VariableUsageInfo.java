package exm.stc.frontend;

import java.util.*;
import java.util.Map.Entry;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.exceptions.VariableUsageException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.util.TernaryLogic.Ternary;

/**
 * A module to perform analysis of variable usage in a compound function
 * * Detect whether a variable is assigned
 * * Detect whether a variable is assigned in all branches of conditional
 * * Detect how an array is assigned (by reference or index)
 * TODO:
 * * Perform index-level analysis of arrays (for static indices at least)
 *
 * Assumptions:
 * * Shadowed variable declarations are not allowed
 * * References to undeclared variables will be caught elsewhere
 * * Type-checking is performed elsewhere
 */

public class VariableUsageInfo {

  private final HashMap<String, VInfo> vars;
  private final ArrayList<Violation> violations;

  public VariableUsageInfo() {
    vars = new HashMap<String, VInfo>();
    violations = new ArrayList<Violation>();
  }

  private VariableUsageInfo(HashMap<String, VInfo> vars,
      ArrayList<Violation> violations) {
    super();
    this.vars = vars;
    this.violations = violations;
  }

  public List<Violation> getViolations() {
    return Collections.unmodifiableList(violations);
  }

  public VInfo lookupVariableInfo(String name) {
    return this.vars.get(name);
  }

  public Violation declare(String file, int line, String name,
                                            SwiftType type) {
    if (vars.get(name) != null) {
      return new Violation(ViolationType.ERROR, "Variable " + name +
            " declared twice", file, line);
    }

    vars.put(name, new VInfo(type, name, true));

    return null;
  }

  public void assign(String file, int line, String name) {
    complexAssign(file, line, name, null, 0);
  }

  /**
   * An all-purpose method to register assignments involving
   * plain variables, struct fields and array indices
   * @param file
   * @param line
   * @param name
   * @param fieldPath
   * @param arrayDepth
   */
  public void complexAssign(String file, int line, String name,
      List<String> fieldPath, int arrayDepth) {
    if (!vars.containsKey(name)) {
      violations.add(new Violation(ViolationType.ERROR, "Variable " +
          name + " not yet declared", file, line));
    } else {
      List<Violation> v = vars.get(name).assign(file, line,
                                    fieldPath, arrayDepth);
      if (v != null) violations.addAll(v);
    }

  }


  /**
   * Called when a read to a variable occurs
   *
   * @param file
   * @param line
   * @param name
   */
  public void read(String file, int line, String name) {
    if (!vars.containsKey(name)) {
      violations.add(new Violation(ViolationType.ERROR, "Variable " +
          name + " not yet declared", file, line));
    } else {
      vars.get(name).read(file, line, null, 0);
    }
  }


  /**
   * Called when a read to a variable occurs e.g.
   *  X.fielda.fieldb[1][2][3]
   *  In the above example fieldPath would be ["fielda", "fieldb"] and
   *  arrDepth would be 3
   * TODO: handle array index reads separately
   * @param file
   * @param line
   * @param name
   * @param fieldPath
   * @param arrDepth
   */
  public Violation complexRead(String file, int line, String name,
      LinkedList<String> fieldPath, int arrDepth) {
    if (!vars.containsKey(name)) {
      violations.add(new Violation(ViolationType.ERROR, "Variable " +
          name + " not yet declared", file, line));
    } else {
      return vars.get(name).read(file, line, fieldPath, arrDepth);
    }
    return null;
  }

  /**
   * Create a variableusage info for a nested scope by doing a deep copy of
   * this object, but setting the usage info to empty
   * @return
   */
  public VariableUsageInfo createNested() {
    HashMap<String, VInfo> vars = new HashMap<String, VInfo>();

    for (VInfo v: this.vars.values()) {
      vars.put(v.getName(), v.makeEmptyCopy(false));
    }
    ArrayList<Violation> violations = new ArrayList<Violation>();
    // Don't want to duplicate violations
    return new VariableUsageInfo(vars, violations);
  }

  /**
   * Merge in results of variable analysis from nested scopes
   * @param file
   * @param line
   * @param nested a list of variableusageinfo objects for a set of mutually
   *                exclusive nested scopes
   * @param exhaustive whether the scopes are also exhaustive (i.e. whether
   *                  one of the scopes is definitely entered)
   */
  public void mergeNestedScopes(String file, int line,
                  List<VariableUsageInfo> nested, boolean exhaustive) {
    // Add all violations in
    for (VariableUsageInfo vu: nested) {
      this.violations.addAll(vu.getViolations());
    }


    /* Check each variable individually.  We can ignore any new variables that
     * were introduced in nested scopes */
    ArrayList<VInfo> nestedVs = new ArrayList<VInfo>();
    for (VInfo v: vars.values()) {
      String vName = v.getName();
      nestedVs.clear();
      for (VariableUsageInfo vu: nested) {
        VInfo nv = vu.lookupVariableInfo(vName);

        // All variables in this scope should be in nested scopes
        assert(nv != null);
        nestedVs.add(nv);
      }

      ArrayList<Violation> problems =
            v.mergeBranchInfo(file, line, nestedVs, exhaustive);

      // Merge the info for this particular variable
      this.violations.addAll(problems);
    }
  }

  /**
   * Should be called when scope is finalised: add warnings if variables
   * are not yet assigned or errors if they are assigned but not read.
   *
   */
  public void detectVariableMisuse(String file, int line) {
    for (VInfo v: this.vars.values()) {
      if (!v.wasDeclaredInCurrentScope()) {
        // variables from outer scopes might be read or written elsewhere
        continue;
      }
      /* This works for both arrays and scalars, because isRead/isAssigned
       * can detect whether
       * With arrays, this can't detect which particular indices were read or written
       */
      if (v.isRead() != Ternary.FALSE) {
        // v might be read
        if (Types.isArray(v.type) && v.maxReadDepth == 0) {
          // ok to refer to entire array if not written
        } else if (Types.isStruct(v.type)) {
          // check this elsewhere in the incompletely defined
          //  struct portion
        } else {
          if (v.isAssigned() == Ternary.FALSE) {
            violations.add(new Violation(ViolationType.ERROR,
                "Variable " + v.getName() + " never written and is read " +
                "on some code paths", file, line));
          }
          else if (v.isAssigned() == Ternary.MAYBE) {
              violations.add(new Violation(ViolationType.WARNING,
                  "Variable " + v.getName() + " might be read and not written, "
                + "possibly leading to deadlock", file, line));
          }
        }
      }
      else {
        // v isn't read, but still should issue warnings
        violations.add(new Violation(ViolationType.WARNING,
              "Variable " + v.getName() + " is not used", file, line));
        if (v.isAssigned() == Ternary.FALSE) {
          violations.add(new Violation(ViolationType.WARNING,
              "Variable " + v.getName() + " is not written", file, line));
        } else if (v.isAssigned() == Ternary.MAYBE) {
          violations.add(new Violation(ViolationType.WARNING,
              "Variable " + v.getName() + " might not be written", file,
                                                                  line));
        }
      }
      List<Violation> more = v.isIncompletelyDefinedStruct(file, line);
      if (more != null) violations.addAll(more);
    }
  }


  public enum ViolationType {
    ERROR,
    WARNING
  }

  /**
   * Represent an invalid or suspect use of a variable
   */
  public static class Violation {
    private final ViolationType type;
    private final String message;
    private final String file;
    private final int line;

    public Violation(ViolationType type, String message, String file, int line) {
      super();
      this.type = type;
      this.message = message;
      this.file = file;
      this.line = line;
    }

    public ViolationType getType() {
      return type;
    }

    public String getMessage() {
      return message;
    }

    public String getFile() {
      return file;
    }

    public int getLine() {
      return line;
    }

    public UserException toException() {
      String fullMessage;
      if (type == ViolationType.ERROR) {
        fullMessage = "Variable usage error. ";
      } else {
        fullMessage = "Variable usage warning. ";
      }
      fullMessage += message;
      return new VariableUsageException(file, line, fullMessage);
    }
    
    public String toString() {
      return "Violation: " + this.type + ": " + message;
    }
  }

  /**
   * Track whether a named variable has been assigned to multiple times
   */
  public static class VInfo {
    private final SwiftType type;

    /*
     * really need to handle struct specially:
     * detect if any invalid fields are read
     * detect if struct isn't completely assigned
     */
    private final Map<String, VInfo> structFields;

    private final boolean declaredInCurrentScope;
    private final String name;
    private Ternary assigned;
    private Ternary read;
    /**
     * The depth of indexing at which array assignment occurred
     */
    private int arrayAssignDepth;
    private int maxReadDepth;

    public VInfo(SwiftType type, String name, boolean locallyDeclared) {
      this(type, locallyDeclared, name, Ternary.FALSE, Ternary.FALSE, 0, -1);
    }

    private VInfo(SwiftType type, Map<String, VInfo> structFields,
        boolean locallyDeclared, String name,
        Ternary assigned, Ternary read, int arrayAssignDepth,
        int maxReadDepth) {
      this.type = type;
      this.structFields = structFields;
      this.declaredInCurrentScope = locallyDeclared;
      this.name = name;
      this.assigned = assigned;
      this.read = read;
      this.arrayAssignDepth = arrayAssignDepth;
      this.maxReadDepth = maxReadDepth;

    }

    private VInfo(SwiftType type, boolean locallyDeclared, String name,
        Ternary assigned, Ternary read, int arrayAssignDepth, int maxReadDepth) {
      this.type = type;
      if (Types.isStruct(type)) {
        structFields = new HashMap<String, VInfo>();
        for (StructField f:  ((StructType)type).getFields()) {
          structFields.put(f.getName(),
              new VInfo(f.getType(), name + "." + f.getName(), locallyDeclared));
        }
      } else {
        structFields = null;
      }
      this.declaredInCurrentScope = locallyDeclared;
      this.name = name;
      this.assigned = assigned;
      this.read = read;
      this.arrayAssignDepth = arrayAssignDepth;
      this.maxReadDepth = maxReadDepth;
    }

    public String getName() {
      return name;
    }

    /**
     * returns TRUE if the variable is assigned (for structs, this means
     *  if all members are assigned), MAYBE if it isn't assigned on some branches
     *  or if only some struct members are assigned
     * @return
     */
    public Ternary isAssigned() {
      if (assigned == Ternary.TRUE) {
        return Ternary.TRUE;
      } else if (structFields != null) {
        // Check fields to see if assigned partially or fully
        int assignedCount = 0;
        int maybeAssignedCount = 0;
        for (VInfo fvi: structFields.values()) {
          if (fvi.isAssigned() == Ternary.TRUE) {
            assignedCount++;
            maybeAssignedCount++;
          } else if (fvi.isAssigned() == Ternary.MAYBE) {
            maybeAssignedCount++;
          }
        }
        if (assignedCount == structFields.size()) {
          // Structure is fully defined
          return Ternary.TRUE;
        } else if (maybeAssignedCount > 0) {
          return Ternary.MAYBE;
        } else {
          return assigned;
        }
      } else {
        return assigned;
      }
    }

    /**
     * Helper function to check if it is either read
     * or assigned
     * @return
     */
    public Ternary isUsed() {
      return Ternary.or(isAssigned(), isRead());
    }

    public boolean wasDeclaredInCurrentScope() {
      return declaredInCurrentScope;
    }

    public int getArrayAssignDepth() {
      return arrayAssignDepth;
    }

    /**
     * make a copy, except it might be in a different scope
     * All of the usage tracking is reset
     *
     * @param
     * @return
     */
    public VInfo makeEmptyCopy(boolean locallyDeclared) {
      HashMap<String, VInfo> structFieldsNew = null;
      if (structFields != null) {
        structFieldsNew = new HashMap<String, VInfo>();
        for (Entry<String, VInfo> e: structFields.entrySet()) {
          structFieldsNew.put(e.getKey(), e.getValue().makeEmptyCopy(locallyDeclared));
        }
      }

      return new VInfo(type, structFields,
          locallyDeclared, name, Ternary.FALSE, Ternary.FALSE, 0, -1);
    }


    /**
     * Called when any assignment occurs (i.e x = ...).
     * These assignments can take the form:
     *  x(.<struct field>)*([<array_index>])*
     *  e.g.
     *  myVar = ...
     *  myVar.x.y = ...
     *  myVar[0] = ...
     *  myVar.x.y.z[0][1][2] = ..
     *  but not:
     *  myVar[0].x
     * @param file
     * @param line
     * @param name the name of the variable(e.g. x)
     * @param fieldPath the path of struct fields (can be null for no path)
     * @param arrayDepth the number of array indices at end of assignment
     */
    public List<Violation> assign(String file, int line,
        List<String> fieldPath, int arrayDepth) {
      if (fieldPath != null && fieldPath.size() > 0) {
        return structAssign(file, line, fieldPath, arrayDepth);
      } else if (arrayDepth > 0) {
        return arrayAssign(file, line, arrayDepth);
      } else {
        // Assigning to the whole variable
        assert(arrayDepth == 0);
        assert(fieldPath == null || fieldPath.size() == 0);
        return plainAssign(file, line);
      }
    }


    private List<Violation> plainAssign(String file, int line) {
      if (assigned == Ternary.TRUE) {
        // There will definitely be a double assignment
        return Arrays.asList(new Violation(
            ViolationType.ERROR, "Variable " + name
            + " cannot be written: it already has been assigned a value",
            file, line));
      } else {
        // Let assignment proceed
        ArrayList<Violation> res = new ArrayList<Violation>();
        if (assigned == Ternary.MAYBE) {
          res.add(new Violation(ViolationType.WARNING,
              "Possible double write of " + "variable " + name, file, line));
        }
        if (Types.isStruct(type)) {
          // All internal fields are assigned with this action
          for (VInfo fieldVI: this.structFields.values()) {
            fieldVI.plainAssign(file, line);
          }
        }
        this.assigned = Ternary.TRUE;
        return res;
      }
    }


    private List<Violation> arrayAssign(String file, int line, int arrayDepth) {
      // Assigning to an index of the array
      if (assigned != Ternary.FALSE) {
        if (arrayAssignDepth != arrayDepth) {
          return Arrays.asList(makeArrDepthViolation(file, line, arrayDepth));
        }
        assigned = Ternary.TRUE;
      } else {
        assigned = Ternary.TRUE;
        arrayAssignDepth = arrayDepth;
      }
      return null;
    }

    private Violation makeArrDepthViolation(String file, int line,
        int arrayDepth) {
      return new Violation(ViolationType.ERROR,
          "Array assignment, indexing at depth" + arrayDepth
          + " when previous assignment was at index depth " +
              arrayAssignDepth, file, line);
    }


    private List<Violation> structAssign(String file, int line,
        List<String> fieldPath, int arrayDepth) {
      VInfo fieldVInfo;
      if (structFields != null) {
        if (this.assigned != Ternary.FALSE) {
          return Arrays.asList(new Violation(ViolationType.ERROR,
              "Assigning to field of variable " + this.name +
              " which was already assigned in full", file, line));
        }
        String field = fieldPath.get(0);

        if (structFields.containsKey(field)) {
          fieldVInfo = structFields.get(field);
        } else {
          return Arrays.asList(new Violation(ViolationType.ERROR,
            "Tried to assign to field " + field + " of variable " +
                this.name + " but field doesn't exist in struct type "
                + ((StructType)type).getTypeName(), file, line));
        }
      } else {
        return Arrays.asList(new Violation(ViolationType.ERROR,
            "Tried to assign to field of variable " +
                this.name + " but variable isn't a struct", file, line));
      }
      fieldPath.remove(0);
      return fieldVInfo.assign(file, line, fieldPath, arrayDepth);
    }

    public Violation read(String file, int line,
        LinkedList<String> fieldPath, int arrDepth) {

      if (fieldPath != null && fieldPath.size() > 0) {
        // handle struct read
        String field = fieldPath.getFirst();
        VInfo fieldVInfo;
        if (structFields != null) {
          if (structFields.containsKey(field)) {
            fieldVInfo = structFields.get(field);
          } else {
            return new Violation(ViolationType.ERROR,
              "Tried to read field " + field + " of variable " +
                  this.name + " but field doesn't exist in struct type "
                  + ((StructType)type).getTypeName(), file, line);
          }
        } else {
          return new Violation(ViolationType.ERROR,
              "Tried to read field " + field + " of variable " +
                  this.name + " but variable isn't a struct", file, line);
        }
        fieldPath.removeFirst();
        Violation v = fieldVInfo.read(file, line, fieldPath, arrDepth);
        fieldPath.addFirst(field); // restore to old state
        this.read = Ternary.TRUE;
        return v;
      } else {
        // array or plain read
        this.read = Ternary.TRUE;
        this.maxReadDepth = Math.max(maxReadDepth, arrDepth);
        return null;
      }
    }

    public VInfo getFieldVInfo(String fieldName) {
      if (structFields == null)  {
        return null;
      }
      VInfo vi = structFields.get(fieldName);
      return vi;
    }

    public Set<String> getFieldNames() {
      if (structFields == null) {
        return null;
      }
      return structFields.keySet();
    }

    public Ternary isRead() {
      return read;
    }

    /**
     * Handles the situation where there are nested scopes
     * @param branches list of a set of mutually exclusive nested scopes
     * @param exhaustive are these branches exhaustive
     * @return
     */
    public ArrayList<Violation> mergeBranchInfo(String file, int line, List<VInfo> branches,
                                      boolean exhaustive) {
      if (branches.size() == 0) {
        throw new STCRuntimeError("branches in mergeBranchInfo had size 0");
      }

      mergeBranchReadInfo(branches, exhaustive);

      ArrayList<Violation> errs = mergeBranchWriteInfo(file, line, branches, exhaustive);
      if (structFields != null) {
        for (VInfo fieldVI: this.structFields.values()) {
          List<VInfo> vis = new ArrayList<VInfo>(branches.size());
          for (int i = 0; i < branches.size(); i++) {
            vis.add(branches.get(i).getFieldVInfo(fieldVI.getName()));

          }

          errs.addAll(fieldVI.mergeBranchInfo(file, line,
                                        branches, exhaustive));
        }
      }
      return errs;
    }

    /**
     * merge in information about reads that occur in nested contexts
     * @param branches
     * @param exhaustive
     */
    private void mergeBranchReadInfo(List<VInfo> branches, boolean exhaustive) {
      // First check to see if variable is read
      Ternary willBeRead;
      int branchMaxReadDepth;
      if (exhaustive) {
        willBeRead = branches.get(0).read;
        branchMaxReadDepth = branches.get(0).maxReadDepth;
      } else {
        willBeRead = Ternary.FALSE;
        branchMaxReadDepth = -1;
      }

      // Check to see whether the variable be definitely read/not read,
      //  or whether it depends on the branch
      for (int i = 0; i < branches.size(); i++) {
        VInfo otherBranch = branches.get(i);
        willBeRead = Ternary.consensus(willBeRead, otherBranch.read);
        branchMaxReadDepth = Math.max(branchMaxReadDepth, otherBranch.maxReadDepth);
      }
      this.read = Ternary.or(this.read, willBeRead);

      /* TODO: this might not be totally right (generating an error instead
       * of warning) e.g. in weird situations like the below:
       *  int x[];
       *  // never assign to x
       *  if (1 == 0) {
       *    trace(x[0]);
       *  }
       */
      this.maxReadDepth = Math.max(this.maxReadDepth, branchMaxReadDepth);

    }

    /**
     * Merge in information about writes to this variable that can occur
     * in nested contexts
     * @param file
     * @param line
     * @param branches
     * @param exhaustive
     * @return
     */
    private ArrayList<Violation>  mergeBranchWriteInfo(String file, int line,
        List<VInfo> branches, boolean exhaustive) {
      ArrayList<Violation> result = new ArrayList<Violation>();
      /* We need to know whether the variable will be assigned or not all
       * code paths, or whether the assignment status depends on the code
       * path
       */
      Ternary assignedInBranch;
      /* we keep track of the depth of indexing used for array assignment:
       * currently we assume that it is the same across branches (TODO) */
      int expectAssignedDepth;
      if (exhaustive) {
        VInfo firstBranch = branches.get(0);
        assignedInBranch = firstBranch.assigned;
        expectAssignedDepth = firstBranch.arrayAssignDepth;
      } else {
        /* Shouldn't be assigned on any branch, because if we don't enter any branch,
         * no assignment occurs*/
        assignedInBranch = Ternary.FALSE;
        expectAssignedDepth = 0;
      }

      for (int i = 0; i < branches.size(); i++) {
        VInfo currBr = branches.get(i);

        if (assignedInBranch == Ternary.FALSE) {
          // Assume this branch's assignment index depth should be used in
          // all branches
          expectAssignedDepth = currBr.arrayAssignDepth;
        }

        // Check to make sure assignment depth is the same across branches
        if (assignedInBranch != Ternary.FALSE &&
                  currBr.assigned != Ternary.FALSE) {
          if (currBr.arrayAssignDepth != expectAssignedDepth) {
              result.add(new Violation(ViolationType.ERROR, "Variable " + name
                  + " is assigned at different subscript levels on different "
                  + " branches", file, line));
          }
        }

        // Update the assignment state of variable
        assignedInBranch = Ternary.consensus(assignedInBranch,
                                              currBr.assigned);
      }

      // First handle the clear situations
      if (assignedInBranch == Ternary.FALSE) {
        // was not touched in branches
        return result;
      } else if (this.assigned == Ternary.FALSE) {
        // was only touched in branches
        this.assigned = assignedInBranch;
        this.arrayAssignDepth =  expectAssignedDepth;
        return result;
      } else if (Types.isArray(this.type)) {
        // Arrays can be assigned multiple times, the depth just has to match
        this.assigned = Ternary.or(assigned, assignedInBranch);
        if (this.arrayAssignDepth != expectAssignedDepth) {
          result.add(makeArrDepthViolation(file, line, expectAssignedDepth));
        }
        return result;
      } else {
        // Non-array type
        if (Ternary.and(assignedInBranch, this.assigned) ==
                                                          Ternary.TRUE) {
          result.add(new Violation(ViolationType.ERROR, "Variable " + name
              + " is assigned twice", file, line));
           return result;
        }
        if (assignedInBranch == Ternary.MAYBE) {
          result.add(new Violation(ViolationType.WARNING, "Variable " + name
              + " is assigned on some code paths but not others", file, line));
        }
        if ((this.assigned != Ternary.FALSE &&
                                            assignedInBranch == Ternary.MAYBE)
            || (assignedInBranch != Ternary.FALSE &&
                                            this.assigned == Ternary.MAYBE)) {
          result.add(new Violation(ViolationType.WARNING, "Variable " + name
              + " might be assigned twice", file, line));
        }
        this.assigned = Ternary.or(this.assigned, assignedInBranch);
        return result;
      }
    }

    /**
     * Called once all usage info is collected
     * @return violations if this is a struct and it is incompletely specified
     */
    public List<Violation> isIncompletelyDefinedStruct(String file, int line) {
      if (Types.isStruct(this.type)) {

        ArrayList<Violation> result = new ArrayList<Violation>();
        if (assigned == Ternary.TRUE) {
          return result;
        } else if (assigned == Ternary.MAYBE) {
         result.add(new Violation(ViolationType.WARNING,
             this.getName() + " might not be assigned", file, line));
         return result;
        }

        for (VInfo vi: structFields.values()) {
          if (vi.isAssigned() != Ternary.TRUE) {
            if (vi.isAssigned() == Ternary.FALSE &&
                  vi.isRead() == Ternary.TRUE) {
              // certain deadlock
              result.add(new Violation(ViolationType.ERROR,
                  "Deadlock detected: " + vi.getName() + " is "
                 + " never assigned but is read", file, line));
            } else {
              result.add(new Violation(ViolationType.WARNING,
                  vi.getName() + " is not guaranteed to be written to"
                  + ", this has potential to cause havoc!", file, line));
            }
          }
          List<Violation> more = vi.isIncompletelyDefinedStruct(file, line);
          if (more != null) result.addAll(more);
        }

        return result;
      } else {
        return null;
      }
    }

    public SwiftType getType() {
      return type;
    }
    
    public String toString() {
      return "VInfo: " + this.name;
    }
  }


}
