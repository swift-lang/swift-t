
import io;
import unix;

// NOTE: Tcl's exec raises an error if the command writes ANYTHING to
// stderr, even on success.  So this test fails on output that has
// nothing to do with the task script, such as a dynamic loader warning
// from the shell (e.g. an LD_LIBRARY_PATH holding a mismatched
// libtinfo makes /bin/bash warn on every invocation).  If this test
// starts failing, check the run's stderr before suspecting the test
// logic.  Adding 2>@1 or -ignorestderr to the exec would tolerate it.

(file o[]) task(file i, int n) "turbine" "0.1"
[
"""
set f [ swift_filename &<<i>> ]
exec ./317-array-build.task.sh $f <<n>>;
set L [ glob test-317-*.data ];
set <<o>> [ swift_array_build $L file ];
"""
 ];

main
{
  printf("OK");
  file i<"input.txt">;
  file o[];
  o = task(i, 10);
  foreach f in o
  {
    printf("output file: %s", filename(f));
  }
  i = touch();
}
