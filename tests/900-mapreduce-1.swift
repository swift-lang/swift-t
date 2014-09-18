
// SKIP-THIS-TEST
// Crashes STC on bag operation

/**
   MR-2
*/

import blob;
import io;
import string;
import sys;

(file o) ingest(string s)
{
  o = input(s);
}

(int outputId[], file f[]) mapF(int i, file f1)
"mr2" "0.0"
[
----
    set A [ mr2::mapF <<i>> <<f1>> ]
    set <<outputId>> [ lindex $A 0 ]
    set <<f>>        [ lindex $A 1 ]
----
];

() reduceF(bag<file> g[])
"mr2" "0.0"
[
----
    puts <<g>>
----
];


main
{
  data  = argv("data");
  count = toint(argv("count"));

  bag<file> M[];

  foreach i in [0:count-1]
  {
    file f1 = ingest(sprintf("%s/input-%i.txt", data, i));

    file f[];
    int outputId[];
    // Each transform produces a variable number of indexed files
    (outputId, f) = mapF(i, f1);
    foreach d,j in outputId
    {
      printf("outputId: %i:%i %s", j, d, filename(f[d]));
    }

    foreach slot in outputId
    {
      printf("slot: %i", slot);

      // Apparently fails here:
      M[slot] += f[slot];
    }
  }

  foreach g in M
  {
    // reduceF(g);
  }
}
