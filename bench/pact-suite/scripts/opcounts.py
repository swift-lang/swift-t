#!/usr/bin/env python2.7
import fileinput
import re
import sys

adlb_op_re = re.compile(r"ADLB: handle: caller=(\d*) (\w*)\(\d*\)$")
hang_check = re.compile(r"WAITING TRANSFORMS: ")
trace = re.compile(r"trace:.*")

debug = False

hung = False

opcounts = {}

for line in fileinput.input():
  if hang_check.search(line):
    hung = True
  
  if hung:
    sys.stderr.write(line)
    continue
  
  if trace.match(line):
    sys.stderr.write(line)
  
  match = adlb_op_re.search(line)
  if match:
    caller = int(match.group(1))
    op = match.group(2)
    if debug:
      print "caller: %d op: %s" % (caller, op)

    prev_count = opcounts.get(op, 0)
    opcounts[op] = prev_count + 1

sorted_opcounts = sorted(opcounts.items())

for op, count in sorted_opcounts:
  print op, count
