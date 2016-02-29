
import io;
import stats;


@dispatch=WORKER
(int sum) g(int i1, int i2) "g" "0.0"
  [ "set <<sum>> [ g <<i1>> <<i2>> ]" ];

int d[];
foreach i in [0:5] {
  d[i] = g(i, 5-i);
}

y = sum_integer(d);
printf("y: %i", y);
