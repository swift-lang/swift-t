// An output flag, with and without a default.  A default names the file
// the program writes when the flag is not given; it is still only a name,
// so nothing has to exist beforehand either way.

import io;
import files;

flags(output a, output b = "7196-output-2.b.out");

a = write("flagged-output");
b = write("defaulted-output");

printf("a=%s b=%s", filename(a), filename(b));
