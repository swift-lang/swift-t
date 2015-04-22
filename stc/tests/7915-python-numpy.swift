
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Python+Numpy
// installed

import io;
import python;
import string;

global const string numpy = "from numpy import *\n\n";

typedef matrix string;

(matrix A) eye(int n)
{
  string command = sprintf("repr(eye(%i))", n);
  string code = numpy+command;
  matrix t = python(code);
  A = replace_all(t, "\n", "", 0);
}

(matrix R) add(matrix A1, matrix A2)
{
  string command = sprintf("repr(%s+%s)", A1, A2);
  string code = numpy+command;
  matrix t = python(code);
  R = replace_all(t, "\n", "", 0);
}

main
{
  matrix A1 = eye(3);
  matrix A2 = eye(3);
  matrix sum = add(A1, A2);
  printf("2*eye(3)=%s", A1);
}
