
import io;
import files;
import sys;
import assert;

//usage: stc 563-glob.swift -S=/home/zzhang/*.swift
main
{
  file s[];	
  s = glob(argv("S"));
  foreach f in s
  {
    printf("file: %s", filename(f));	
  }

  file t[];
  t = glob("A.pattern.that.does.not.match.anything.*");
  assertEqual(size(t), 0, "size(t)");
}
