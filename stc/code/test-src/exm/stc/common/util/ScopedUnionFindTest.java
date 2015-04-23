package exm.stc.common.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import exm.stc.common.util.ScopedUnionFind.UnionFindSubscriber;

public class ScopedUnionFindTest {

  @Test
  public void testBasic() {
    ScopedUnionFind<Integer> uf = buildBasic();

    // Check non-existent returns self
    assertEquals(1234, (int)uf.lookup(1234));
  }

  private ScopedUnionFind<Integer> buildBasic() {
    ScopedUnionFind<Integer> uf = ScopedUnionFind.createRoot();
    Set<Integer> affected;

    // Should return self
    assertEquals(1, (int)uf.lookup(1));

    // Basic merge
    affected = uf.merge(10, 20);
    assertEquals(new HashSet<Integer>(Arrays.asList(20)),
                 affected);

    assertEquals(10, (int)uf.lookup(10));
    assertEquals(10, (int)uf.lookup(20));

    // Merge into loser
    affected = uf.merge(20, 30);
    assertEquals(new HashSet<Integer>(Arrays.asList(30)),
        affected);

    assertEquals(10, (int)uf.lookup(20));
    assertEquals(10, (int)uf.lookup(30));

    // Merge into winner
    affected = uf.merge(10, 15);
    assertEquals(new HashSet<Integer>(Arrays.asList(15)),
                 affected);

    assertEquals(10, (int)uf.lookup(10));
    assertEquals(10, (int)uf.lookup(15));

    // Duplicate - should be no affected
    affected = uf.merge(10, 20);
    assertEquals(Collections.emptySet(), affected);

    // Merge into new winner
    affected = uf.merge(5, 10);

    assertEquals(new HashSet<Integer>(Arrays.asList(10, 15, 20, 30)),
                 affected);

    assertEquals(5, (int)uf.lookup(5));
    assertEquals(5, (int)uf.lookup(10));
    assertEquals(5, (int)uf.lookup(30));

    return uf;
  }

  @Test
  public void testScoped() {
    ScopedUnionFind<Integer> uf = buildBasic();

    buildChild(uf);
  }

  private ScopedUnionFind<Integer> buildChild(ScopedUnionFind<Integer> uf) {
    ScopedUnionFind<Integer> child = uf.newScope();

    assertEquals(5, (int)child.lookup(5));
    assertEquals(5, (int)child.lookup(10));
    assertEquals(5, (int)child.lookup(30));

    child.merge(4, 5);

    // Change should occur in child but not parent
    assertEquals(4, (int)child.lookup(5));
    assertEquals(4, (int)child.lookup(10));
    assertEquals(4, (int)child.lookup(30));

    assertEquals(5, (int)uf.lookup(5));
    assertEquals(5, (int)uf.lookup(10));
    assertEquals(5, (int)uf.lookup(30));

    return child;
  }

  /**
   * Basic test for propagating merges to child
   */
  @Test
  public void testPropagateChild1() {
    ScopedUnionFind<Integer> uf = buildBasic();
    ScopedUnionFind<Integer> child = buildChild(uf);

    // Merge in parent
    Set<Integer> affected = uf.merge(1, 4);

    // Only 4 should be affected, since unmerged in parent
    assertEquals(affected, new HashSet<Integer>(Arrays.asList(4)));

    // Change should occur in parent *and* child
    assertEquals(5, (int)uf.lookup(5));
    assertEquals(5, (int)uf.lookup(10));
    assertEquals(5, (int)uf.lookup(30));

    assertEquals(1, (int)child.lookup(5));
    assertEquals(1, (int)child.lookup(10));
    assertEquals(1, (int)child.lookup(30));

    // Somewhat conflicting merge - already merged in child
    affected = uf.merge(1, 5);

    // Everything attached to 5 should be affected
    assertEquals(new HashSet<Integer>(Arrays.asList(5, 10, 15, 20, 30)),
                  affected);

    assertEquals(1, (int)uf.lookup(5));
    assertEquals(1, (int)uf.lookup(10));
    assertEquals(1, (int)uf.lookup(30));

    assertEquals(1, (int)child.lookup(5));
    assertEquals(1, (int)child.lookup(10));
    assertEquals(1, (int)child.lookup(30));

    // Check that we get correct list
    Set<Integer> members = child.members(1);
    System.err.println(members);
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 4, 5, 10, 15, 20, 30)),
                  members);
  }


  @Test
  public void testPropagateChild2() {
    ScopedUnionFind<Integer> uf = buildBasic();
    ScopedUnionFind<Integer> child = buildChild(uf);

    // Merge in parent
    Set<Integer> affected = uf.merge(4, 7);

    assertEquals(new HashSet<Integer>(Arrays.asList(7)), affected);

    assertEquals(4, (int)uf.lookup(7));
    assertEquals(4, (int)uf.lookup(4));

    assertEquals(4, (int)child.lookup(7));
    assertEquals(4, (int)child.lookup(4));


    // Merge non-existent
    affected = uf.merge(100, 200);

    assertEquals(new HashSet<Integer>(Arrays.asList(200)), affected);

    assertEquals(100, (int)uf.lookup(100));
    assertEquals(100, (int)uf.lookup(200));

    assertEquals(100, (int)child.lookup(100));
    assertEquals(100, (int)child.lookup(200));
  }


  /**
   * Test merging
   */
  @Test
  public void testPropagateChild3() {
    ScopedUnionFind<Integer> uf = buildBasic();
    ScopedUnionFind<Integer> child = buildChild(uf);

    Set<Integer> affected;

    affected = child.merge(100, 200);
    assertEquals(new HashSet<Integer>(Arrays.asList(200)), affected);

    // Merge non-canonical in parent - should get 100, 200, 300 in same set
    affected = uf.merge(50, 200);
    assertEquals(new HashSet<Integer>(Arrays.asList(200)), affected);

    assertEquals(50, (int)uf.lookup(50));
    assertEquals(100, (int)uf.lookup(100));
    assertEquals(50, (int)uf.lookup(200));

    assertEquals(50, (int)child.lookup(50));
    assertEquals(50, (int)child.lookup(100));
    assertEquals(50, (int)child.lookup(200));
  }

  @Test
  public void testNotification() {
    ScopedUnionFind<Integer> uf = buildBasic();
    ScopedUnionFind<Integer> child = buildChild(uf);

    NotificationSaver<Integer> subscriber = new NotificationSaver<Integer>();

    child.subscribe(4, true, subscriber);
    child.subscribe(4, false, subscriber);

    child.merge(3, 4);

    assertEquals(2, subscriber.notifs.size());
    assertEquals((Integer)3, subscriber.notifs.get(0).val1);
    assertEquals((Integer)4, subscriber.notifs.get(0).val2);
    assertEquals((Integer)3, subscriber.notifs.get(1).val1);
    assertEquals((Integer)4, subscriber.notifs.get(1).val2);
  }

  private static class NotificationSaver<T> implements UnionFindSubscriber<T> {
    final List<Pair<T, T>> notifs = new ArrayList<Pair<T, T>>();

    @Override
    public void notifyMerge(T winner, T loser) {
      notifs.add(Pair.create(winner, loser));
    }

  }
}
