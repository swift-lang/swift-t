package exm.stc.ic.refcount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.AliasTracker;
import exm.stc.ic.opt.AliasTracker.Alias;
import exm.stc.ic.opt.AliasTracker.AliasKey;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

/**
 * Class to keep track of information relevant to refcount pass
 */
public class RCTracker {
  
  /**
   * Current read increments per var
   */
  private final Counters<AliasKey> readIncrements;
  
  /**
   * Current read decrements per var (negative numbers)
   */
  private final Counters<AliasKey> readDecrements;
  
  /**
   * Current write increments per var
   */
  private final Counters<AliasKey> writeIncrements;
  
  /**
   * Current write decrements per var (negative numbers)
   */
  private final Counters<AliasKey> writeDecrements;
  
  private final AliasTracker aliases;
  
  /**
   * List of variables that we've created to store struct elements
   */
  private Set<Var> createdTemporaries = new HashSet<Var>();  
  
  public RCTracker() {
    this(null);
  }
  
  public RCTracker(AliasTracker parentAliases) {
    this.readIncrements =  new Counters<AliasKey>();
    this.readDecrements =  new Counters<AliasKey>();
    this.writeIncrements =  new Counters<AliasKey>();
    this.writeDecrements =  new Counters<AliasKey>();
    if (parentAliases != null) {
      this.aliases = parentAliases.makeChild();
    } else {
      this.aliases = new AliasTracker();
    }
  }

  public AliasTracker getAliases() {
    return aliases;
  }

  public Set<Var> getCreatedTemporaries() {
    return createdTemporaries;
  }

  public void updateForInstruction(Instruction inst) {
    for (Alias alias: aliases.getInstructionAliases(inst)) {
      addStructElem(alias.parent, alias.field, alias.child);
    }
  }

  /**
   * Record that parent.field == child
   * 
   * @param parent
   * @param field
   * @param child
   */
  private void addStructElem(Var parent, String field, Var child) {
    assert(child != null);
    assert(parent != null);

    aliases.addStructElem(parent, field, child);
    
    // This may bind a struct path to a concrete variable, which may mean
    // that, e.g. if the variable is a constant, that it no longer has
    // a refcount.
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      if (!RefCounting.hasRefCount(child, rcType)) {
        reset(rcType, child);
      }
    }
  }

  /**
   * Update counts with a set of changes
   * @param changes
   * @param rcType
   */
  public void merge(Counters<Var> changes, RefCountType rcType,
                    RCDir dir) {
    Counters<AliasKey> changes2 = new Counters<AliasKey>();
    for (Entry<Var, Long> e: changes.entries()) {
      changes2.add(getCountKey(e.getKey()), e.getValue());
    } 
    getCounters(rcType, dir).merge(changes2);
  }

  /**
   * In case where a variable is part of multiple structs, canonicalize the
   * paths so we can merge refcount operations
   */
  public void canonicalize() {
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (RCDir dir: RCDir.values()) {
        Iterator<Entry<AliasKey, Long>> it =
                          rcIter(rcType, dir).iterator();
        // Add these back in at end
        List<Pair<AliasKey, Long>> toAdd = 
                                  new ArrayList<Pair<AliasKey,Long>>();
        while (it.hasNext()) {
          Entry<AliasKey, Long> e = it.next();
          
          // Canonicalize key by finding var, and then finding first path
          // that a var is a part of
          AliasKey currKey = e.getKey();
          Var v = getRefCountVar(null, currKey, false);
          if (v != null) {
            AliasKey canonKey = getCountKey(v);
            if (!canonKey.equals(currKey)) {
              // Replace with canonical
              it.remove();
              toAdd.add(Pair.create(canonKey, e.getValue()));
            }
          }
        }
        
        
        for (Pair<AliasKey, Long> e: toAdd) {
          incrKey(e.val1, rcType, e.val2, e.val1.type());
        }
      }
    }
  }
  
  private Counters<AliasKey> getCounters(RefCountType rcType, RCDir dir) {
    if (rcType == RefCountType.READERS) {
      if (dir == RCDir.INCR) {
        return readIncrements;
      } else {
        return readDecrements;
      }
    } else {
      assert(rcType == RefCountType.WRITERS);
      if (dir == RCDir.INCR) {
        return writeIncrements;
      } else {
        return writeDecrements;
      }
    }
  }
  
  /**
   * Return list of variables that need to be incremented/decremented.
   * Modifying this doesn't affect underlying map
   * @param rcType
   * @return
   */
  public Counters<Var> getVarCandidates(Block block, RefCountType rcType,
                                        RCDir dir) {
    Counters<Var> result = new Counters<Var>();
    for (Entry<AliasKey, Long> e: rcIter(rcType, dir)) {
      result.add(getRefCountVar(block, e.getKey(), true), e.getValue());
    }
    return result;
  }

  /**
   * Get iterator over reference counts.  Modifying this will affect
   * underlying map.
   * @param rcType
   * @param dir
   * @return
   */
  public Iterable<Entry<AliasKey, Long>> rcIter(RefCountType rcType,
                                                RCDir dir) {
    return getCounters(rcType, dir).entries();
  }

  public Var getRefCountVar(Block block, AliasKey key,
                            boolean createIfNotPresent) {
    // See if we have a variable already for that path
    Var result = aliases.findVar(key);
    
    if (result != null) {
      return result;
    } else if (createIfNotPresent) {
      assert(block != null);
      return createStructFieldTmp(block, key);
    } else {
      return null;
    }
  }


  /**
   * If we don't have a variable corresponding to struct path, 
   * then create a temporary variable that will be added later to IR
   * @param block
   * @param key
   * @return
   */
  private Var createStructFieldTmp(Block block, AliasKey key) {
    Var curr = key.var;
    for (int i = 0; i < key.structPath.length; i++) {
      assert(Types.isStruct(curr.type()));
      String fieldName = key.structPath[i];
      Var child = aliases.findVar(curr, fieldName);
      if (child == null) {
        // Doesn't exist
        StructType parentType = (StructType)curr.type();
        Type elemType = parentType.getFieldTypeByName(fieldName);
        String varName = OptUtil.optVPrefix(block, 
            Var.STRUCT_FIELD_VAR_PREFIX + curr.name() + "_" + fieldName); 
        child = new Var(elemType, varName, Alloc.ALIAS, DefType.LOCAL_COMPILER,
                        VarProvenance.structField(curr, fieldName));
        createdTemporaries.add(child);
        
        // Must add to block to avoid duplicates later
        block.addVariable(child);
        addStructElem(curr, fieldName, child);
      }
      curr = child;
    }
    return curr;
  }

  public void reset(RefCountType rcType, Var v, RCDir dir) {
    getCounters(rcType, dir).reset(getCountKey(v));
  }
  
  public void reset(RefCountType rcType, Var v) {
    for (RCDir dir: RCDir.values()) {
      getCounters(rcType, dir).reset(getCountKey(v));
    }
  }

  public void resetAll(RefCountType rcType, Collection<? extends Var> vars,
                       RCDir dir) {
    for (Var var: vars) {
      reset(rcType, var, dir);
    }
  }
  
  public void resetAll() {
    for (RCDir dir: RCDir.values()) {
      resetAll(dir);
    }
  }
  
  public void resetAll(RCDir dir) {
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      getCounters(rcType, dir).resetAll();
    }
  }

  public long getCount(RefCountType rcType, AliasKey k, RCDir dir) {
    return getCounters(rcType, dir).getCount(k);
  }
  
  public long getCount(RefCountType rcType, Var v, RCDir dir) {
    return getCount(rcType, getCountKey(v), dir);
  }

  void readIncr(Var var) {
    readIncr(var, 1);
  }
  
  void readDecr(Var var) {
    readIncr(var, -1);
  }
  
  void readIncr(Var var, long amount) {
    incr(var, RefCountType.READERS, amount);
  }
  
  void readDecr(Var var, long amount) {
    readIncr(var, -1 * amount);
  }
  
  void writeIncr(Var var) {
    writeIncr(var, 1);
  }
  
  void writeDecr(Var var) {
    writeIncr(var, -1);
  }
  
  void writeDecr(Var var, long amount) {
    writeIncr(var, -1 * amount);
  }
  
  void writeIncr(Var var, long amount) {
    incr(var, RefCountType.WRITERS, amount);
  }

  public void cancel(Var var, RefCountType rcType, long amount) {
    cancel(getCountKey(var), rcType, amount);
  }
  
  public void cancel(AliasKey key, RefCountType rcType,
                    long amount) {
    RCDir dir = RCDir.fromAmount(-amount);
    Counters<AliasKey> counters = getCounters(rcType, dir);
    
    long oldCount = counters.add(key, amount) - amount;
    // Check we don't overshoot
    if (oldCount < 0) {
      assert(oldCount + amount <= 0) : oldCount + " + " + amount;
    } else {
      assert(oldCount + amount >= 0) : oldCount + " + " + amount;
    }
  }

  void incr(Var var, RefCountType rcType, long amount) {
    if (Types.isStruct(var.type())) {
      incrStructMembers(var, rcType, amount);
    } else if (RefCounting.hasRefCount(var, rcType)) {
      AliasKey key = getCountKey(var);
      incrDirect(key, rcType, amount);
    }
  }

  /**
   * Increment a key directly, without trying to parse out structs
   * This should only be used if the key has already been validated.
   * @param key
   * @param rcType
   * @param amount
   */
  void incrDirect(AliasKey key, RefCountType rcType, long amount) {
    getCounters(rcType, RCDir.fromAmount(amount)).add(key, amount);
  }

  void decr(Var var, RefCountType type, long amount) {
    incr(var, type, -1 * amount);
  }
  
  /**
   * Increment key by given amount
   * @param key
   * @param rcType
   * @param amount
   * @param varType
   */
  public void incrKey(AliasKey key, RefCountType rcType, long amount,
                      Type varType) {
    if (Types.isStruct(varType)) {
      incrStructMembersRec(key, (StructType)varType, rcType, amount);
    } else {
      // Check to see if var/type may be able to carry refcount
      Var var = getRefCountVar(null, key, false);
      if ((var != null && RefCounting.hasRefCount(var, rcType)) ||
          (var == null &&
           RefCounting.mayHaveRefcount(varType, rcType))) {
        incrDirect(key, rcType, amount);
      }
    }
  }

  private void incrStructMembers(Var var, RefCountType rcType, long amount) {
    StructType t = (StructType)var.type();
    incrStructMembersRec(getCountKey(var), t, rcType, amount);
  }
  
  /**
   * recursively walk struct type and increment fields
   * @param result
   * @param key
   * @param type
   * @param amount
   */
  private void incrStructMembersRec(AliasKey key, StructType type,
      RefCountType rcType, long amount) {
    for (StructField f: type.getFields()) {
      AliasKey newKey = key.makeChild(f.getName());
      Type fieldType = f.getType();
      incrKey(newKey, rcType, amount, fieldType);
    }
  }

  /**
   * Find the key we're using to track refcounts for this var
   * @param var
   * @return
   */
  public AliasKey getCountKey(Var var) {
    // See if this is within an enclosing struct, and if so find
    // topmost struct

    return aliases.getCanonical(var);
  }
  
  @Override
  public String toString() {
    return "ReadIncr: " + readIncrements +
            "\n    ReadDecr: " + readDecrements +
           "\n    WriteIncr: " + writeIncrements +
           "\n    WriteDecr: " + writeDecrements +
           "\n    " + aliases;
  }
}