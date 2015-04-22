// Test checkpointing for data structures
import assert;
import math;
import stats;
import blob;
import string;

main {

  int a;
  float A[];
  bag<int> abag;

  a, A, abag = arrayf(10, [blob_from_string("hello"),
                           blob_from_string("world")]);
  assertEqual(a, 10 + 6, "a");
  assertEqual(A[0], 6.0, "A[0]");
  assertEqual(bag_size(abag), 2, "bag_size(abag)");

  bag<int> baga[], ibag;
  baga[0] = ibag;
  baga[1] = ibag;
  ibag += 1;
  ibag += 2;
  ibag += 2;
  
  foreach slist in g(baga) {
    trace("slist: " + string_join(slist, ", "));
  }

  blob B1[] = h([blob_from_string("hello"),
               blob_from_string("world")]);
  assertEqual(string_from_blob(B1[0]), "hello", "B1[0]");
  assertEqual(string_from_blob(B1[1]), "world", "B1[1]");
  assertEqual(size(B1), 2, "size(B1)");
}


@checkpoint
(int a, float A[], bag<int> abag) arrayf (int b, blob B[]) {
  trace("arrayf executed args: " + fromint(b));
  foreach x, i in B {
    A[i] = itof(blob_size(x));
    abag += blob_size(x);
  }
  a = b + toInt(sum_float(A)/itof(size(A)));
}


// NOTE: this test sometimes fails since i isn't sorted into
// canonical order
@checkpoint
(bag<string[]> o) g (bag<int> i[]) {
  trace("g executed");
  o += [fromint(bag_size(i[0]))];
  o += ["1", "2", "3"];
  o += ["4", "5", "6"];
  o += ["7", "8", "9"];
}

@checkpoint
(blob o[]) h (blob i[]) {
  trace("h executed");
  o = i;
}
