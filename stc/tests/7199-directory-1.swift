// directory() binds a file the same way input() does, but checks that the
// path is a directory as well as that it exists.  The check must survive
// optimization: naming the directory is the usual reason to bind one, so a
// program that does nothing but ask for filename() still has to be checked.

import files;

arguments(string d);

file dir = directory(d);

trace("directory is " + filename(dir));
