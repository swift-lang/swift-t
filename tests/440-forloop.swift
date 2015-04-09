/* Regression test - combination of for loop and global scope */

int x;

for (x = 0; x < 1; x = x + 1) { }

trace(x);
