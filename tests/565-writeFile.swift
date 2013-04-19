
import io;
import files;
import sys;


main
{
  file f <"test.tmp">;
  f = writeFile("hello world!");
  string s;
  s = readFile(f);
  printf("%s", s);
}

