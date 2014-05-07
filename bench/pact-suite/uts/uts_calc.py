
# Estimate number of nodes for geometric uts with linear shape

b_0 = 4.0
gen_mx = 45
nodes = 8192
cores_per_node = 32

# level is from 0 onwards
def level_nodes(level):
  branch_factors = [ b_0 * ( 1.0 - i / float(gen_mx)) for i in range(level + 1) ]
  return reduce(lambda x, y: x*y, branch_factors)

print "b_0: %f gen_mx %d" % (b_0, gen_mx)

total = 0
for level in range(gen_mx+1):
  this_level_count = level_nodes(level)
  print "Level %d count: %d" % (level, this_level_count)
  total += this_level_count

print "Total count: %d" % total
node_secs = float(total) / 1e6
print "Core-seconds (at 1M nodes/sec): %.1f" % (node_secs)
secs = node_secs / (nodes * cores_per_node)
print "Seconds (at %d nodes and %d cores/node): %.1f" % \
      (nodes, cores_per_node, secs )
mins = secs / 60.0
print "Minutes: %.1f" % mins
