// SKIP-THIS-TEST

import location;
import io;

@dispatch=WORKER
(string o) workf() "turbine" "0.0" [ 
  "set <<o>> test"
];

PY=locationFromRank(0);

s1 = @location=PY workf();

wait(s1)
{
  int N = 10;
  for (int i = 0; i < N ; i = i + 1) {
    printf("hello");
  }
}

