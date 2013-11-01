
import io;

(file o[]) task(file i, int n) "turbine" "0.1"
[
"""
puts OK1
exec ./316-array-build.task.sh <<n>>
puts OK2
set L [ glob test-316-*.data ]
puts "L: $L"
puts OK3
turbine::swift_array_build <<o>> $L file
"""
];  

main
{
  printf("OK");
  file i = input("input.txt"); 
  file o[];
  o = task(i, 10);
  foreach f in o
  {
    printf("output file: %s", filename(f));
  }
}

