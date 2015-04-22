import io;

/*
 * Regression test to check for bug where optimizer inserted
 * wait into itself
 */

type struct {
  int member;
}

(struct b) make() {
  b.member = 1;
}

main {
  struct X = make();
  wait (X.member) {
    if (X.member == 0) {
      printf("DONE!");
    }
  }
}
