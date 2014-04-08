#!/usr/bin/env python2.7
import sys

files = sys.argv[1:]

keys = set()
fileVals = []
for file in files:
  vals = {}
  fileVals.append(vals)
  try:
    for line in open(file).readlines():
      k, v = line.split()
      vals[k] = v
      keys.add(k)
  except Exception, e:
    print "Error in line \"%s\" of file %s" % (line, file)
    raise e


for key in sorted(keys):
  sys.stdout.write(key)
  for vals in fileVals:
    sys.stdout.write("\t")
    sys.stdout.write(str(vals.get(key, 0)))
  sys.stdout.write("\n")
