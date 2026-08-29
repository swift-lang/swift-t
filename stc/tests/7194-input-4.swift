// An input argument appears in the usage message exactly as any other
// argument of its kind does: the argument type says how the value is read,
// not how it is written on the command line.

import files;

arguments(input data : "the file to read",
          description : "print the name of a file named on the command line");
flags(input extra = "extra.txt" : "another file to read");

trace(filename(data), filename(extra));
