// THIS-TEST-SHOULD-NOT-RUN
// What the input argument type is for: input() checks that the file
// exists, so a filename that names nothing aborts the program where the
// argument is bound, rather than wherever it is first read.

import io;
import files;

arguments(input data);

printf("unreachable: %s", read(data));
