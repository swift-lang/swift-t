package exm.stc.jvm.runtime;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Vars {
  public static abstract class BaseVar {
    private final AtomicInteger writers;
    
    public BaseVar(int writers) {
      this.writers = new AtomicInteger(writers);
    }
    
    public void incrWriters(String fn, String varName, int amount) 
        throws DoubleWriteException {
      if (amount == 0)
        return;
      long prev = writers.getAndAdd(amount);
      if (prev <= 0) {
        throw new DoubleWriteException("Variable " + varName + 
            " written after close in function " + fn + "!");
      }
      long curr = prev - amount;
      if (curr < 0) {
        JVMRuntime.getLogger().warn("Decremented " + varName + " below 0 "
                    + " in function " + fn + " " + prev + " - " + amount);
      }
      if (curr <= 0) {
        notifyWaiters();
      }
    }

    // TODO: more memory-efficient way?
    // TODO: lock-free way?  AtomicReference?
    ArrayList<NotifyTarget> notifications = null;
    /**
     * 
     * @param target
     * @return true if subscribed, false if closed
     */
    public synchronized boolean subscribe(NotifyTarget target) {
      // TODO: I think this avoids race condition with notifications
      if (writers.get() <= 0) {
        return false;
      }
      
      if (notifications == null)
        notifications = new ArrayList<NotifyTarget>();
      notifications.add(target);
      
      return true;
    }
    
    private synchronized void notifyWaiters() {
      for (NotifyTarget t: notifications)
        t.notifyFinal(this);
      
      notifications = null;
    }
  }
  
  public static interface NotifyTarget {
    /**
     * Notify that var has been finalized
     * @param var
     */
    public void notifyFinal(BaseVar var);
  }
  
  public abstract static class ScalarVar extends BaseVar {
    public ScalarVar(int writers, boolean isSet) {
      super(writers);
      this.isSet = isSet;
    }
    
    /**
     * Whether value is available
     */
    protected boolean isSet = false;
  }
  
  public static class IntVar extends ScalarVar {
    public IntVar(int writers) {
      super(writers, false);
    }
    
    public IntVar(int writers, long value) {
      super(writers, true);
      this.value = value;
    }

    private long value;
    
    public long get(String fn, String varName) throws InvalidReadException {
      // Should subscribe before reading, creating memory barrier
      if (isSet) {
        return value;
      } else {
        throw new InvalidReadException(varName + " was read before writing " 
            + " in function " + fn);
      }
    }
    
    /**
     * 
     * @param fn
     * @param varName
     * @param value
     * @throws DoubleWriteException 
     */
    public void set(String fn, String varName, long value)
                                      throws DoubleWriteException {
      set(fn, varName, value, 1);
    }

    public void set(String fn, String varName, long value,
                     int writersDecr) throws DoubleWriteException {
      this.value = value;
      this.incrWriters(fn, varName, -1 * writersDecr);
    }
  }
}
