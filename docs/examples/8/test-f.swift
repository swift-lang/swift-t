
import io;

@par @dispatch=WORKER (float z) f(int k) "f" "0.0" "f_tcl";

main
{
  float z = @par=2 f(3);
  printf("z: %0.3f", z);
}
