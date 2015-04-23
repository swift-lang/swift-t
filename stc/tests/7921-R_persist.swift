
// SKIP-THIS-TEST
// this should work but we do not assume everyone has R installed

// This test shows that you can reuse the R interpreter
// As far as I know, you cannot re-initialize the R interpreter from C
// -Justin 2015/04

import io;
import string;
import R;

global const string template =
"""
  x <- %i
  a <- a+x+100
  cat("the answer is: ", a, "\\n")
  a
""";

R("a <- 1") =>
{
  code = sprintf(template, 4);
  s1 = R(code);
  printf("the answer was: %i", s1);
  s2 = R(code);
  printf("the answer was: %i", s2);
}
