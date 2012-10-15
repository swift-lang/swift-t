
/*
 * checks.h
 *
 *  Created on: Jun 11, 2012
 *      Author: wozniak
 *
 *  Various macros for easier error checking and reporting
 *
 *  The CHECK macros never report an error message in a correct
 *  program: thus, they may be disabled by NDEBUG for performance
 * */

#ifndef CHECKS_H
#define CHECKS_H

#ifndef NDEBUG

/**
  Asserts that condition is true, else returns given error code.
  Note: This is disabled if NDEBUG is defined
*/
#define CHECK_MSG(rc, args...)                  \
  { if (!(rc)) {                                             \
      printf("CHECK FAILED: %s:%i\n", __FILE__, __LINE__);   \
      printf(args);                                          \
      return ADLB_ERROR; }}

/**
   Checks that an MPI return code is MPI_SUCCESS
   Note: This is disabled if NDEBUG is defined
 */
#define MPI_CHECK(rc)  \
  { if (rc != MPI_SUCCESS) { \
    printf("MPI_CHECK FAILED: %s:%i\n", __FILE__, __LINE__);\
    return ADLB_ERROR; }}

/**
   Checks that an ADLB return code is ADLB_SUCCESS
   Note: This is disabled if NDEBUG is defined
   If used in nested functions that all return adlb_code, can
   create something like a stack trace
 */
#define ADLB_CHECK(rc) { \
  if (!(rc == ADLB_SUCCESS || rc == ADLB_NOTHING)) { \
    printf("ADLB_CHECK FAILED: %s:%i\n", __FILE__, __LINE__); \
    return rc; }}

/**
   Checks that an ADLB data return code is ADLB_DATA_SUCCESS
   Note: This is disabled if NDEBUG is defined
 */
#define ADLB_DATA_CHECK(dc) { \
  if (dc != ADLB_DATA_SUCCESS) { \
    printf("ADLB_DATA_CHECK FAILED: %s:%i\n", __FILE__, __LINE__); \
    return ADLB_ERROR; }}

#else

// Make these noops for performance
#define CHECK_MSG(rc, args...)
#define MPI_CHECK(rc)
#define ADLB_CHECK(rc)
#define ADLB_DATA_CHECK(rc)
#endif

#endif
