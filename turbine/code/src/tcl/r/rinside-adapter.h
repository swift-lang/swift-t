
#ifndef RINSIDE_ADAPTER_H
#define RINSIDE_ADAPTER_H

#include <stdbool.h>

// Eclipse cannot mix C with C++:
// Set preprocessor macro ECLIPSE to prevent scanning this file
#ifndef ECLIPSE

#ifdef __cplusplus
#ifndef DEPS
// Do not look at the C++ header dependencies in here:
#include <RInside.h>
#endif
#endif

#ifdef __cplusplus
using namespace std;
extern "C"
{
#endif
  bool use_rinside_void(const char* code);
  bool use_rinside_expr(const char* code, char** result, int* length);
#ifdef __cplusplus
}
#endif

#endif
#endif
