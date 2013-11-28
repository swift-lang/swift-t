%module f

%include "f.h"

%{

  typedef int MPI_Comm;
  #include "f.h"
%}

typedef int MPI_Comm;
