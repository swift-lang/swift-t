
package require f

set N 3

set A [ new_string_array ]
string_array_string_array_create $A $N
for { set i 1 } { $i <= $N } { incr i } {
    string_array_string_array_set $A $i arg$i
}

set output [ malloc_double ]

FortFuncs_func $N $A $output

puts -nonewline stderr "output: "
print_double $output

free_double $output
