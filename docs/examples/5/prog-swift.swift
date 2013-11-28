
import io;

// SNIPPET 1
(float v) func(string A[]) "f" "0.0"
[
  "set <<v>> [ func <<A>> ]"
];
// SNIPPET END

main
{
  string A[] = [ "arg1", "arg2", "arg3" ];
  v = func(A);
  printf("output: %.2f", v);
}
