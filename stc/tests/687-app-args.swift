
string echo = "/bin/echo";

// Check handling of ambiguous types
app (file out) echoTest () {
    echo 1 [2,3,4] [[5,6],[7,8]] [[],[]] [[[9]]] @stdout=out 
}


import assert;
import files;
import string;

assertEqual(trim(read(echoTest())), "1 2 3 4 5 6 7 8 9", "echoTest");
