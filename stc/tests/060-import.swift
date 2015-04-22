/*
 * Regression test for compiler bug with temporary naming at top level
 * in different files
 */

import include.import_060;

import assert;

assertEqual(1 + 1, 2, "");
