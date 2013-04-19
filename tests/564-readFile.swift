
import io;
import files;
import sys;

//usage stc 563-readFile.tcl
main
{
  file f = input_file("/etc/passwd");	
  string s = readFile(f);
  printf("%s\n", s);
}
