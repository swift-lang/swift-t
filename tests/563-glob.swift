
#include <builtins.swift>
#include <io.swift>
#include <files.swift>
#include <sys.swift>

#usage: stc 563-glob.swift -S=/home/zzhang/*.swift
main
{
  string s[];	
  s = glob(argv("S"));
  foreach f in s
  {
    printf("file: %s", f);	
  }
}
