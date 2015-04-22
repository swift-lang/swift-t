from math import sqrt
for x, y in [(2**i, int(round(sqrt(2**i)*300))) for i in range(13)]: print x, y

