
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Julia installed

import io;
import julia;
import string;
import sys;

main {
  start = clock();
  f =
"""
begin
 f(x) = begin
          sleep(1)
          x+1
        end
 f(%s)
end
""";
  s1 = julia(sprintf(f, 1));
  s2 = julia(sprintf(f, 2));
  s3 = julia(sprintf(f, 3));
  printf("julia results: %s %s %s", s1, s2, s3);
  wait (s1, s2, s3) {
    printf("duration: %0.2f", clock()-start);
  }
}
