
import io;
/*
ret_type swift_app_func() pkg_name version tcl_func
*/
(int v) swift_main(string A[]) "swift_main" "0.0" "swift_main_wrap";

main
{
  string A[] = [ "arg1", "arg2", "arg3" ];
  rc = swift_main(A); // rc gets its type implicitly
  printf("exit code: %i", rc);
}
