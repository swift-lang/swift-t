// arguments() for every supported type other than string/int:
// float, boolean, file and url.

import io;
import files;

arguments(float x, boolean flag, file data, url u);

printf("x=%.1f flag=%s data=%s u=%s",
       x, flag, filename(data), urlname(u));
printf("contents=%s", read(data));
