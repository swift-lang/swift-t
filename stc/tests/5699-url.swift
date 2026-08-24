// Check that input_url() performs no filesystem check, unlike input().
//
// The named file deliberately does not exist.  A url is only a name:
// input_url_impl() in turbine/code/lib/files.tcl does set_filename_val
// and closes the file, with no stat.
//
// The contrast is visible at O0, where the same program written with
// input() aborts with:
//   input_file: file '5699-url-nonexistent.txt' does not exist
// At O1 and above both calls are eliminated as dead code -- input() and
// input_url() are declared @pure, and urlname()/filename() fold to the
// mapping without consulting the file -- so this test only has teeth at
// O0.  If that changes, this comment should be revisited.

import assert;
import io;

url g = input_url("5699-url-nonexistent.txt");

assertEqual(urlname(g), "5699-url-nonexistent.txt", "urlname");

trace("url: " + urlname(g));

