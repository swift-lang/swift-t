// Regression test for bag append bug

import blob;
import io;
import assert;
import string;
import sys;

(file o) ingest(string s)
{
  o = input(s);
}

(int outputId[], int f[]) mapF(int i, file f1)
"turbine" "0.0"
[
----
    set <<outputId>> [ dict create <<i>> <<i>> [ expr <<i>> + 1 ] [ expr <<i>> + 1 ] [ expr <<i>> + 2 ] [ expr <<i>> + 2 ] ]
    set <<f>> [ dict create <<i>> <<i>> [ expr <<i>> + 1 ] [ expr <<i>> + 1 ] [ expr <<i>> + 2 ] [ expr <<i>> + 2 ] ]
----
];

(int o) reduceF(bag<int> g)
"turbine" "0.0"
[
  "set <<o>> 0; foreach __x <<g>> { incr <<o>> $__x }"
];


main
{
  data  = argv("data");
  count = toint(argv("count"));

  bag<int> M[];

  foreach i in [0:count-1]
  {
    file f1 = ingest(sprintf("%s/input-%i.txt", data, i));

    int f[];
    int outputId[];
    // Each transform produces a variable number of indexed files
    (outputId, f) = mapF(i, f1);
    foreach d,j in outputId
    {
      printf("outputId: %i:%i %i", j, d, f[d]);
    }

    foreach slot in outputId
    {
      printf("slot: %i", slot);

      M[slot] += f[slot];
    }
  }


  int R[];
  foreach g, i in M
  {
    int r = reduceF(g);
    // All elements should be i
    printf("M[" + i + "] = " + repr(g)) =>
    assertEqual(r, i * bag_size(g), "Check " + i);
    R[i] = r;
  }
}
