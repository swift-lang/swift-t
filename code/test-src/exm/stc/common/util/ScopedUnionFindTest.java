package exm.stc.common.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScopedUnionFindTest {

  @Test
  public void testBasic() {
    ScopedUnionFind<Integer> uf = buildBasic();
  }

  private ScopedUnionFind<Integer> buildBasic() {
    ScopedUnionFind<Integer> uf = ScopedUnionFind.createRoot();

    // Should return self
    assertEquals(1, (int)uf.lookup(1));

    // Basic merge
    uf.merge(10, 20);

    assertEquals(10, (int)uf.lookup(10));
    assertEquals(10, (int)uf.lookup(20));

    // Merge into loser
    uf.merge(20, 30);

    assertEquals(10, (int)uf.lookup(20));
    assertEquals(10, (int)uf.lookup(30));

    // Merge into winner
    uf.merge(10, 15);

    assertEquals(10, (int)uf.lookup(10));
    assertEquals(10, (int)uf.lookup(15));

    // Merge into new winner
    uf.merge(5, 10);

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

  @Test
  public void testPropagateChild() {
    ScopedUnionFind<Integer> uf = buildBasic();
    ScopedUnionFind<Integer> child = buildChild(uf);

    // Merge in parent
    // TODO: does this conceptually make sense? what if conflicting merges made?
    uf.merge(1, 4);
    uf.merge(1, 5);

    // Change should occur in parent *and* child
    assertEquals(1, (int)uf.lookup(5));
    assertEquals(1, (int)uf.lookup(10));
    assertEquals(1, (int)uf.lookup(30));

    assertEquals(1, (int)child.lookup(5));
    assertEquals(1, (int)child.lookup(10));
    assertEquals(1, (int)child.lookup(30));
  }
}
