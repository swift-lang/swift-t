import location;
import io;

@dispatch=WORKER
(string o) workf() "turbine" "0.0" [ 
  "set <<o>> test"
];

s1 = @location=randomWorker() workf();

wait(s1)
{
  int N = 10;
  for (int i = 0; i < N ; i = i + 1) {
    printf("hello " + i);
  }
}

