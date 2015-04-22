// Test include works
import io;


#include "include/header-620.swift"

// Test import works
import include.module_620;
// Check double import
import include.module_620;

main {
  printf("INCLUDE: %i", t(1));
  printf("IMPORT: %i", u(1));

}
