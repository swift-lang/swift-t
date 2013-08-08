// COMPILE-ONLY-TEST
// THIS-TEST-SHOULD-NOT-COMPILE
// Regression test for internal error on undeclared var


import files;
import io;
import string;
import sys;

// NOTE: _outB4 is not declared.  In previous version of STC this caused
// a NullPointerException
app (file _outB1, file _outB2, file _outB3) A (file _inA1, file _inA2, file _inA3){
    "mimo.sh" @_inA1 @_inA2 @_inA3 @_outB1 @_outB2 @_outB3 @_outB4;
}

() main () {

    file in_a1=input_file("sample.txt");
    file in_a2=input_file("sample.txt");
    file in_a3=input_file("sample.txt");
    file in_a4=input_file("sample.txt");

    foreach i in [0:1]{
       string outb1=sprintf("/tmp/swift.work/outb1-%i.dat", i);
       string outb2=sprintf("/tmp/swift.work/outb2-%i.dat", i);
       string outb3=sprintf("/tmp/swift.work/outb3-%i.dat", i);
       string outb4=sprintf("/tmp/swift.work/outb4-%i.dat", i);

       file o1 <outb1>;
       file o2 <outb2>;
       file o3 <outb3>;
       file o4 <outb4>;

       (o1, o2, o3, o4) = A (in_a1, in_a2, in_a3, in_a4);

    }
}
