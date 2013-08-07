
import io;
import string;

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
  string s = string_join(A, ":");
  printf("s:%s", s);
}
