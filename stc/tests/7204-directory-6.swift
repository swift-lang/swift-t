// A directory flag, with and without a default, alongside an input flag:
// the two argument types differ only in how strictly the path is checked.

import io;

flags(directory a, directory b = "7204-directory-6.b.dir", input f);

printf("a=%s b=%s f=%s", filename(a), filename(b), filename(f));
