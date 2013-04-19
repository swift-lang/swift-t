import assert;
import blob;

main {
    blob x = blob_from_string("hello world");
    int res = f(x, x);
    assertEqual(res, 1, "res");
    string msg = string_from_blob(x);
    assertEqual(msg, "hello world", "msg");
}


(int r) f (blob x, blob y) {
    r = 1;
}
