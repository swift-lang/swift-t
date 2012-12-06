
#include <builtins.swift>
#include <io.swift>
#include <files.swift>
#include <sys.swift>

//usage: stc 563-glob.swift -S=/home/zzhang/*.swift
main
{
  file s[];	
  s = glob(argv("S"));
  foreach f in s
  {
    printf("file: %s", filename(f));	
  }
}
