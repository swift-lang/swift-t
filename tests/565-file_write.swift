
import io;
import files;
import sys;


main
{
  file f <"test.tmp">;
  f = write("hello world!");
  string s;
  s = read(f);
  printf("%s", s);
}

