import numpy

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

first10 = [fib(i) for i in range(10)]

print "first10: " + str(first10)
print "mean(first10): " + str(numpy.mean(first10))
print "var(first10): " + str(numpy.var(first10))
print "var(first10) * n: " + repr(numpy.var(first10) * len(first10))

first15 = [fib(i) for i in range(15)]

print "first15: " + str(first15)
print "mean(first15): " + str(numpy.mean(first15))
print "var(first15): " + str(numpy.var(first15))
print "var(first15) * n: " + repr(numpy.var(first15) * len(first15))
print "std(first15): " + repr(numpy.std(first15))
