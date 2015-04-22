
import blob;
import io;

(blob y) mvm_blob(blob A, blob x, int n) "mvm" "0.0"
[ "set <<y>> [ mvm::mvm <<A>> <<x>> <<n>> ]" ];

int n = 2;
blob A_blob = blob_read(input_file("A.data"));
blob x_blob = blob_read(input_file("x.data"));
blob y_blob = mvm_blob(A_blob, x_blob, n);
float y[] = floats_from_blob(y_blob);
foreach v, i in y {
  printf("y[%i]=%f", i, v);
}
