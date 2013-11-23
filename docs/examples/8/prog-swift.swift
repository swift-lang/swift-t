
import io;

(float v) func(string A[]) "f" "0.0"
[
  "set <<v>> [ func <<A>> ]"
];

main
{
  string A[] = [ "arg1", "arg2", "arg3" ];
  v = func(A);
  printf("output: %.2f", v);
}
