
// SKIP-THIS-TEST
// this should work but we do not assume everyone has R installed

import io;
import string;
import R;

template =
"""
  x <- %i
  a <- x+100
  cat("the answer is: ", a, "\\n")
""";

code = sprintf(template, 4);
s = R(code, "toString(a)");
printf("the answer was: %i", s);
