import io;

(file o[]) task(file i, int n) "turbine" "0.1"
[
"""
set f [ swift_filename &<<i>> ]
exec ./316-array-build.task.sh $f <<n>>
set L [ glob test-316-*.data ]
set <<o>> [ swift_array_build $L file ]
"""
];  

main
{
  file i = input("input.txt"); 
  file o[];
  o = task(i, 10);
  foreach f in o
  {
    printf("output file: %s", filename(f));
  }
}
