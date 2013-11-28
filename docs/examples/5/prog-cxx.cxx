
#include <iostream>

#include "FortWrap.h"

using namespace std;

int
main(int argc, char* argv[])
{
  cout << "starting prog(argc=" << argc << ")..." << endl;

  // Fortran-compatible argument count:
  argc--;

  string_array A;
  A.string_array_create(argc);

  for (int i = 1; i <= argc; i++)
    A.string_array_set(i, argv[i]);

  double output = -1;
  FortFuncs::func(argc, &A, &output);

  cout << "output is: " << output << endl;

  return 0;
}
