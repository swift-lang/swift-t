
#include <builtins.swift>
#include <io.swift>
#include <files.swift>
#include <sys.swift>


main
{
  file f <"test.tmp">;
  f = writeFile("hello world!");
  string s;
  s = readFile(f);
  printf("%s", s);
}

