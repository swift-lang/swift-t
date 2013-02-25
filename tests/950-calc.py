import numpy
import math

def fib(n):
    if n == 0:
        return 0
    elif n == 1:
        return 1
    else:
        i = 2
        fn1 = 0
        fn = 1
        while i <= n:
            fn2 = fn1
            fn1 = fn
            fn = fn1 + fn2
            i += 1
        return fn


def expected(nums, name):
  vals = [fib(i) for i in nums]
  print name + ": " + str(vals)
  print "mean(" + name + "): " + str(numpy.mean(vals))
  print "var(" + name + "): " + str(numpy.var(vals))
  print "var(" + name + ") * n: " + repr(numpy.var(vals) * len(vals))
  # n, mean, M2
  return (len(vals), numpy.mean(vals), numpy.var(vals) * len(vals))


def combine(x1, x2):
  n1, m1, M2_1 = x1
  n2, m2, M2_2 = x2
  delta = m2 - m1
  n = n1 + n2
  m = m1 + delta * (n2/float(n))
  M2 = M2_1 + M2_2 + delta**2 * (n1 * n2) / float(n)
  return (n, m, M2)


expected(range(10), "first10")
expected(range(15), "first15")

x1 = expected([0, 1], "951-set1")
x2 = expected([2, 10, 11, 3, 4, 7, 8, 9], "951-set2")
x3 = expected([14, 13], "951-set3")
x4 = expected([12, 5, 6], "951-set4")

c1 = combine(x1, x2)
c2 = combine(c1, x3)
c3 = combine(c2, x4)
print "Combine1: " + str(c1)
print "Combine2: " + str(c2)
print "Combine3: " + str(c3)
print "mean: " + str(c3[1]) + " std: " + str(math.sqrt(c3[2] / c3[0]))
