import io;
import location;

main {
  location A[];
  foreach i in [1:10] {
    A[i] = random_worker();
  }

  foreach j in [1:10] {
    printf("%d: %d", j, A[j]);
  }
}
