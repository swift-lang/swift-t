// The input argument type binds an ordinary file, exactly as the file type
// does, and is read through input(), so that the file is checked to exist
// as soon as the argument is bound.  Equivalent to
//   file data = input(argp(1));

import io;
import files;

arguments(input data);

printf("data=%s", filename(data));
printf("contents=%s", read(data));
