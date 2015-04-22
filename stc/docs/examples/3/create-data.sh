> turbine-write-doubles input.data 1 2 3 10

# Check that it worked:
> du -b input.data
32	input.data
> od -t f8 input.data
0000000                        1                        2
0000020                        3                       10
0000040
> turbine-read-doubles input.data
1.0000
2.0000
3.0000
10.0000
