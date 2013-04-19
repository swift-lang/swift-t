import assert;

@dispatch=WORKER
(int o) double(int i) "turbine" "0.0.1" [
  "set <<o>> [ expr <<i>> * 2 ]"
];

@dispatch=WORKER
(int o) triple(int i) "turbine" "0.0.1" [
  "set <<o>> [ expr <<i>> * 3 ]"
];

main {
    int i = 1;
    trace(double(double(double(i))));

    
    assertEqual(double(double(double(i))), 8, "");

    // See how it handles places where it is a forking pipeline
    int x = double(double(double(2)));
    int y = triple(x);
    int z = double(x);
    int q = triple(x);
    trace(q, x, y, z);
}
