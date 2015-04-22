
import io;
import files;
import sys;
import assert;

main
{
  // Test with mapped file
  file f <"test.tmp">;
  f = write("hello world!");
  string s;
  s = read(f);
  printf("%s", s);
  assertEqual(s, "hello world!", "mapped");
  
  // Test with unmapped file
  file f2 = write("testing!");
  printf("TMP FILENAME:%s", filename(f2));
  string s2 = read(f2);
  printf("%s", s2);
  assertEqual(s2, "testing!", "unmapped");
}

