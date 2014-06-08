#!/usr/bin/env python2.7
import fileinput
import re
import sys

#adlb_op_re = re.compile(r"ADLB: handle: caller=(\d*) (\w*)\(\d*\)$")
adlb_op_perf_count = re.compile(r"COUNTER: ([^=]*)=(\d\d*)$")
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
  
  match = adlb_op_perf_count.search(line)
  if match:
    op = match.group(1)
    count = int(match.group(2))
    if debug:
      print "op: %s count: %d" % (op, count)

    prev_count = opcounts.get(op, 0)
    opcounts[op] = prev_count + count

if (len(opcounts) == 0):
  print >> sys.stderr, 'No counter output found, is ADLB_PERF_COUNTERS enabled'

sorted_opcounts = sorted(opcounts.items())

for op, count in sorted_opcounts:
  print op, count
