import sys;

main {

  int A[];
  wait deep (A) {
    trace("DONE 1");
  }
  
  wait (A) {
    trace("DONE 2");
  }

  wait(A[0]) {
    trace("DONE 3");
  }


  A = [1, f(2), f(3)];
}

(int o) f(int i) {
  wait deep (sleep(0.5)) {
    o = i;
  }
}
