
import io;
import unix;

(file o[]) task(file i, int n) "turbine" "0.1"
[
"""
puts "task()...";
exec ./316-array-build.task.sh <<n>>;
set L [ glob test-316-*.data ];
turbine::swift_array_build <<o>> $L file;
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
