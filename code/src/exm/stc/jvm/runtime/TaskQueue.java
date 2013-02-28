package exm.stc.jvm.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The global task queue
 * TODO: priorities
 *
 */
public class TaskQueue {
  
  /**
   * TODO: allow passing in of queues so that each thread
   * can have in own local heap
   * @param numThreads
   */
  public TaskQueue(int numThreads) {
    this.numThreads = numThreads;
    this.targeted = new ArrayList<ConcurrentLinkedDeque<Task>>(numThreads);
    this.regular = new ArrayList<ArrayDeque<Task>>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      this.targeted.add(new ConcurrentLinkedDeque<Task>());
      this.regular.add(new ArrayDeque<Task>());
    }
  }
  
  private final int numThreads;
  
  /**
   * Targeted task queues (one per thread)
   */
  private final ArrayList<ConcurrentLinkedDeque<Task>> targeted;
  
  /**
   * Personal non-targeted task queue (one per thread) 
   * TODO: priorities
   */
  private final ArrayList<ArrayDeque<Task>> regular;
  
  
  /**
   * TODO: each thread should hold local reference to own deque
   * @return a task, or null if nothing available
   */
  public Task getTask(int threadNum) {
    // Targeted have highest priority
    Task res = targeted.get(threadNum).pollLast();
    if (res != null)
      return res;
    
    // Next, try to see if something in local deque
    ArrayDeque<Task> myDeque = regular.get(threadNum);
    synchronized (myDeque) {
      res = myDeque.pollLast();
    }
    if (res != null)
      return res;
    
    // Finally, search other deques to steal work
    // TODO: exit condition - detect idle, or go to sleep
    Random r = new Random();
    while (true) {
      int deque = r.nextInt(numThreads - 1);
      if (deque >= threadNum)
        deque++;
      
      ArrayDeque<Task> otherDeque = regular.get(threadNum);
      // Task from other end
      synchronized (otherDeque) {
        res = otherDeque.pollFirst();
      }
      if (res != null)
        return res;      
    }
  }
  
  
}
