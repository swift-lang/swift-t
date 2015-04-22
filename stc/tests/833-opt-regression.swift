// Test failure when value numbering disabled and dead code elimination enabled

/*
  The problem is that the compiler frontend generates code for the for
  loop where there is a store to an alias variable.  The alias variable
  is simply aliased outside the for loop, so when value numbering is
  enabled it's quickly able to fix up the alias to point to the original
  variable.  Unfortunately, if it's not fixed up, a bug in dead code
  elimination failed to handle the aliasing correctly and thought
  it could safely remove the store.
 */

float param;

for (int i = 0, param=0; i < 1; i = i + 1, param=param2) {
  param2 = param;
}

trace("DONE: " + param);
