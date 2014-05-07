

type arrays {
  int X[];
  float Y[];
  string Z[];
}

main {
  arrays x;

  // Check storing array at struct subscript
  x.X = [1,2,3];
  
  f(x);

  x.Y = [3.142];
  x.Z = ["a", "b", "c"];
}


f (arrays s) {
  print_arrays(s.X, s.Y, s.Z);
}

print_arrays (int a1[], float a2[], string a3[]) "turbine" "0.0.1" [
    "puts [ list <<a1>> <<a2>> <<a3>> ] "
];
