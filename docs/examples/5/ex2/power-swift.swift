
import io;
/*
ret_type swift_app_func() pkg_name version tcl_func
*/
(int v) power_main(string A[]) "power_main" "0.0" "power_main_wrap";

main
{
  string A[] = [ "2" ];
  rc = power_main(A); // rc gets its type implicitly
  printf("exit code: %i", rc);
}
