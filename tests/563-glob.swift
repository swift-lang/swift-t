
import io;
import files;
import sys;

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
