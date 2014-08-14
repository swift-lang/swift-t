// THIS-TEST-SHOULD-NOT-COMPILE
// SKIP-THIS-TEST issue #702

(file o) f(file o) "turbine" "0.0"
[ "exec cp <<o>> <<o>>" ];
  
main
{
  file i = input("i.txt");
  file o<"o.txt">;
  o = f(i);
}
