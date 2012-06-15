/*
 * checks.h
 *
 *  Created on: Jun 11, 2012
 *      Author: wozniak
 *
 *  Various macros for easier error checking and reporting
 *
 *  The CHECK macros never report an error message in a correct
 *  program: thus, they may be disabled for performance
 * */

#ifndef CHECKS_H
#define CHECKS_H

#ifdef ENABLE_DEBUG
/**
  Asserts that condition is true, else returns given error code.
  Note: This is disabled if ENABLE_DEBUG is not defined
*/
#define CHECK_MSG(rc, args...)                  \
  { if (!(rc)) {                                             \
      printf("CHECK FAILED: adlb.c line: %i\n", __LINE__);   \
      printf(args);                                          \
      return ADLB_ERROR; }}

/**
   Checks that an MPI return code is MPI_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define MPI_CHECK(rc)  \
  { if (rc != MPI_SUCCESS) { \
    printf("MPI_CHECK FAILED: adlb.c line: %i\n", __LINE__);\
    return ADLB_ERROR; }}

/**
   Checks that an ADLB return code is ADLB_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define ADLB_CHECK(rc) { if (rc != ADLB_SUCCESS) { \
    printf("ADLB_CHECK FAILED: adlb.c line: %i\n", __LINE__); \
    return rc; }}

/**
   Checks that an ADLB data return code is ADLB_DATA_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define ADLB_DATA_CHECK(dc) { if (dc != ADLB_DATA_SUCCESS) { \
    printf("ADLB_DATA_CHECK FAILED: adlb.c line: %i\n", __LINE__); \
    return ADLB_ERROR; }}
#else

// Make these noops for performance
#define CHECK_MSG(rc, args...)
#define MPI_CHECK(rc)
#define ADLB_CHECK(rc)
#define ADLB_DATA_CHECK(rc)
#endif

#endif
