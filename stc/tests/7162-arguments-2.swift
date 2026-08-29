// arguments() for every supported type other than string/int:
// float, boolean, input and url.  The file the program reads is declared
// input, not file: a file argument is one the program writes.

import io;
import files;

arguments(float x, boolean flag, input data, url u);

printf("x=%.1f flag=%s data=%s u=%s",
       x, flag, filename(data), urlname(u));
printf("contents=%s", read(data));
