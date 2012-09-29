
#include <builtins.swift>
#include <io.swift>
#include <string.swift>
#include <sys.swift>

main {
  argv_accept("v", "a", "exec", "help");

  string program = argv(0);
  printf("program: %s", program);

  int c = argc();
  printf("argc: %i", c);

  string a = argv("a");
  printf("argv a: %s", a);

  string s1 = argv(1);
  printf("argv 1: %s", s1);
  string s2 = argv(2);
  printf("argv 2: %s", s2);

  string e = argv("exec");
  string tokens[] = split(e, " ");
  foreach t in tokens
  {
    printf("token: %s", t);
  }

  if (argv_contains("v"))
  {
    printf("has: v");
  }

  printf("args: %s", args());
}
