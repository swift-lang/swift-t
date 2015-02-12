@dispatch=WORKER
(int sum) g(int i1, int i2) "g" "0.0"
[ "set <<sum>> [ g <<i1>> <<i2>> ]" ];

foreach i in [0:5] {
  int sum = g(i, 5-i);
}
