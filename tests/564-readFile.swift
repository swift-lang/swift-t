
#include <builtins.swift>
#include <io.swift>
#include <files.swift>
#include <sys.swift>

//usage stc 563-readFile.tcl
main
{
  file f = input_file("/etc/passwd");	
  string s = readFile(f);
  printf("%s\n", s);
}
