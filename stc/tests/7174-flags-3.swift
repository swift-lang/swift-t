// input and url flags, read as by input() and input_url()

import io;
import files;

flags(input f = "7174-flags-3.data", url u = "http://example.com/x");

printf("file: %s", read(f));
printf("url: %s", urlname(u));
