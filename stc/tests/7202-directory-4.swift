// A directory argument is input for a directory: it binds a file through
// directory(), which checks that the path is there and is a directory.
// Equivalent to
//   file d = directory(argp(1));

arguments(directory d);

trace(filename(d));
