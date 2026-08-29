// A file argument names a file the program writes: it becomes the mapping
// of an unassigned file variable, so the program supplies the contents.
// output is a synonym for file, and the two may be mixed freely.
// Equivalent to
//   file a <argp(1)>;
//   file b <argp(2)>;

import io;
import files;

arguments(file a, output b);

a = write("written-to-a");
b = write("written-to-b");

printf("a=%s b=%s", filename(a), filename(b));
