// input flags: the argument type means the same thing in flags() as in
// arguments(), whether the filename comes from the command line or from
// the declared default.

import io;
import files;

flags(input a, input b = "7192-input-2.b.data");

printf("a=%s", read(a));
printf("b=%s", read(b));
