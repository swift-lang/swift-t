
import io;
import files;
import sys;

//usage stc 564-file_read.tcl
main
{
  file f = input_file("/etc/passwd");
  string s = read(f);
  printf("%s\n", s);
}
