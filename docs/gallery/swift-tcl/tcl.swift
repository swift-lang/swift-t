import io;

@dispatch=WORKER
(int o) add(int i, int j) "turbine" "0.0"
[
"""
set i <<i>>
set j <<j>>
set o [ expr $i + $j ]
puts "tcl: o=$o"
set <<o>> $o
"""
];

i = 3;
j = 4;
o = add(i,j);
printf("o should be: %i", i+j);
printf("o is: %i", o);
