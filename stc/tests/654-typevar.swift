import sys;

<T> (int handle, T x) mutable_ref() "funcs_654" "2.0" "mutable_ref";

close_mutable_ref(int handle) "funcs_654" "2.0" "close_mutable_ref";

<T> (T x, T y) f() "funcs_654" "2.0" "f";


<T> (T x) g1() "funcs_654" "2.0" "g1";
<T> (T x) g2(T y) "funcs_654" "2.0" "g2";

main () {
  int arr[];
  int ref;
  ref, arr = mutable_ref();

  trace(ref, repr(arr));


  // Matching types
  string r1, r2;
  r1, r2 = f(); 

  // In expression context
  g2(g1());

  // Inferred type
  x = g1();

  wait (sleep(0.1)) {
    close_mutable_ref(ref);
  }
} 
