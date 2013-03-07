#include <builtins.swift>
#include <io.swift>
#include <assert.swift>
#include <sys.swift>

// TODO: implement calc
(float o) calc (int i)
    "stcbench" "0.0.2" [ "set <<o>> [ stcbench::calc <<i>> ]" ];


// TODO: implement do_reduce.  Some variance in runtime?
(float o) do_reduce (float... vals)
    "stcbench" "0.0.2" [ "set <<o>> [ stcbench::do_reduce <<vals>> ]" ];

main {
  float A[];

  argv_accept("size");
  int size = argv("size");

  printf("START: size=%i", size);
  float A[];
  foreach i in [0:size] {
    A[i] = calc(i);
  }

  float final = reduce(A);
  printf("END: final=%f", final);
}


global const int reduce_deg=4;

(float res) reduce2(float A[], int start, int end) {
  int count = end - start;
  assert(count > 0 && count < reduce_deg, "bad count");
  switch (count) {
    case 1:
      res = A[start];
    case 2:
      res = do_reduce(A[start], A[start+1]);
    case 3:
      res = do_reduce(A[start], A[start+1], A[start+2]);
    case 4:
      res = do_reduce(A[start], A[start+1], A[start+2], A[start+3]);
  }
}

(float res) reduce (float A[]) {
  int n = size(A);
  if (n <= reduce_deg) {
    res = reduce2(A, 0, n);
  } else {
    float B[];
    foreach i in [0:n:reduce_deg] {
      B[i %/ reduce_deg ] = reduce2(A, i, max_int(i + reduce_dec, n));
    }
    res = reduce(B);
  }
}
