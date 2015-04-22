import io;
import location;

type wrapped_rank int;

main {
  location A[];
  foreach i in [1:10] {
    A[i] = random_worker();
  }

  foreach j in [1:10] {
    printf("%d: %d", j, wrapped_rank(A[j].rank));
  }
}
