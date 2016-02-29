
#include <stdlib.h>
#include "b.h"

main() {
  double v[4] = { 1, 2, 3, 10 };
  double* sum = b(v, 4);
  free(sum);
}
