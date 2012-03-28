#include "builtins.swift"

// SKIP-THIS-TEST

app (int o) s(int i)
{
  sleep i;
}

main
{
  int k;
  int m;
  k = 2;
  (m) = s(k);
}
