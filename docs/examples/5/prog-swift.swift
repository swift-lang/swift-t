import io;

(int v) swift_main(string A[]) "swift_main" "0.0" "swift_main_wrap";

mainapp swift_main1;

main {
  string A[] = [ "arg1", "arg2", "arg3" ];
  rc = swift_main(A);
  printf("exit code: %i", rc);
}
