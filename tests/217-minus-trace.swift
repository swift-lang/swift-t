#include "builtins.swift"

main
{
  int a;
  int b;
  int c;

  a = 2;
  b = 1;
  (c) = minus_integer(a,b);

  trace(a,b,c);
}
