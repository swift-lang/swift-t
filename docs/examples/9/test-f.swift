
import blob;
import io;
import sys;

printf("args: %s", args());
string s = "MY_OUTPUT";
blob b = blob_from_string(s);
turbine_run_output_blob(b);
