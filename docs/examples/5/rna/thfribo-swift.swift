
import io;
/*
ret_type swift_app_func() pkg_name version tcl_func
*/
(int v) thfribo_main(string A[]) "thfribo_main" "0.0" "thfribo_main_wrap";

main {
  int rc[];
  foreach i in [4801:4900:1]{
	/*string x[] = [fromint(i)];*/
	printf("Started %i th iteration", i);
   	rc[i] = thfribo_main([fromint(i)]); 
 	printf("exit code: %i", rc[i]);
 }
}
