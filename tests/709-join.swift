
import io;
import string;
import assert;

main
{
  string a = "a";
  string b = "b";
  string bc = b+"c";
  string d = "d";
  string A[];
  A[1] = a;
  A[2] = bc;
  A[10] = d;
  A[11] = "e";
  A[12] = "f";
  A[13] = "g";
  string s = string_join(A, ":");
  printf("s:%s", s);

  // Check string_from_floats
  string fs = string_from_floats([1.0,2.5,3.25]);
  printf("fs:%s", fs);
  assertEqual(fs, "1.0,2.5,3.25", "string_from_floats");
}
