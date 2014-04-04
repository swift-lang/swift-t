
import io;
/*
ret_type swift_app_func() pkg_name version tcl_func
*/
(int v) thfribo_main(string A[]) "thfribo_main" "0.0" "thfribo_main_wrap";

main {
  string A[] = [ "dummy" ];
  rc = thfribo_main(A); // rc gets its type implicitly
  printf("exit code: %i", rc);
}
