
# Estimate number of nodes for geometric uts with linear shape

b_0 = 4.0
gen_mx = 22

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

