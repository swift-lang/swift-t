
#include <iostream>

#include "rinside-adapter.h"

static bool initialized = false;
static RInside* R_interpreter = NULL; // (0, NULL);

static inline void
init(void)
{
  if (!initialized)
  {
    R_interpreter = new RInside(0, NULL);
    initialized = true;
  }
}

bool
use_rinside_void(const char* code)
{
  init();
  string c(code);
  try
  {
    R_interpreter->parseEvalQ(c);
  }
  catch (exception& e)
  {
    cout << "R error: " << e.what() << endl;
    return false;
  }
  return true;
}

/*
  Caller must free result.
*/
bool
use_rinside_expr(const char* expr, char** result, int* length)
{
  init();

  int n;
  char* t;

  // Call R
  string c(expr);
  try
  {
    string s = R_interpreter->parseEval(c);
    // Convert result to C string
    n = s.length()+1;
    t = (char*) malloc(n);
    strncpy(t, s.c_str(), n);
  }
  catch (exception& e)
  {
    cout << "R error: " << e.what() << endl;
    return false;
  }

  // Assign return values
  *result = t;
  *length = n;
  return true;
}
