// Formal parameters on main() bind the positional command line arguments,
// exactly as arguments() does.  The parameters are stripped from main(),
// which becomes an ordinary no-argument main() reading the top-level
// variables the expansion declares.

main(string username, int v) {
  trace("hello " + username, " the value is ", v+1);
}
