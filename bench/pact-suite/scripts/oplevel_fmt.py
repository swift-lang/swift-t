#!/usr/bin/env python2.7
import sys

files = sys.argv[1:]

C = "Create"
L = "Scalar Load"
S = "Scalar Store"
Task = "Task Get/Put"
Ins = "Array Insert"
LU = "Array Lookup"
RC = "Refcount"
Sub = "Subscribe"

order = [Task, C, Sub, L, S, LU, Ins, RC]
cats = {
  "CONTAINER_REFERENCE": LU,
  "CREATE_HEADER": C,
  "ENUMERATE": LU,
  "EXISTS": L,
  "GET": Task,
  "INSERT_HEADER": Ins,
  "LOOKUP":   LU,
  "MULTICREATE": C,
  "PUT": Task,
  "REFCOUNT_INCR": RC,
  "RETRIEVE": L,
  "STORE_HEADER": S,
  "SUBSCRIBE": Sub
  }


keys = set()

catVals = {}
for file in files:
  #print "FILE: " + file
  vals = {}
  for line in open(file).readlines():
    k, v = line.split()
    cat = cats[k]
    val = vals.get(cat, 0)
    vals[cat] = val + int(v)
    #print cat + " += " + str(int(v))
  
  for cat in order:
    l = catVals.get(cat, [])
    l.append(vals.get(cat, 0))
    catVals[cat] = l

for cat in order:
  print cat,
  for cv in catVals[cat]:
    print "&", cv,
  print "\\\\"
