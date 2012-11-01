
#include <builtins.swift>
#include <io.swift>
#include <files.swift>
#include <sys.swift>

//usage stc 563-readFile.tcl -F=/home/zzhang/563-readFile.swift
main
{
  string s;	
  s = readFile(argv("F"));
  printf("%s\n", s);
}
