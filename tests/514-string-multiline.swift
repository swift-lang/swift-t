
import io;

f() "turbine" "0.0"
[----
set x 1
puts "x=$x"
----];

main
{
  string t1 =
----
xyxy
1234
----;

string t2 =
"""
xyzz
4321
""";

  printf("OUTPUT: %s %s", t1, t2);
  f();
}
