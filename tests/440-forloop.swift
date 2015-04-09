/* Regression test - combination of for loop and global scope */
// SKIP-THIS-TEST

int x;

for (x = 0; x < 1; x = x + 1) { }

trace(x);
