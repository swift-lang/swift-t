
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.1

namespace import turbine::* turbine::data::*

init

string_init 1
string_init 2
# c::string 3

string_set 1 "hi"
string_set 2 "bye"

set v1 [ string_get 1 ]
set v2 [ string_get 2 ]

puts -nonewline "result: "
turbine::trace 1 2

engine
finalize

puts OK
