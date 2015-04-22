
// SKIP-THIS-TEST
// this should work but we do not assume everyone has R installed

import io;
import string;
import R;

global const string template =
"""
  x <- %i
  a <- x+100
  cat("the answer is: ", a, "\\n")
  a
""";

main
{
  code = sprintf(template, 4);
  s = R(code);
  printf("the answer was: %i", s);
}
