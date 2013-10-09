/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


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

#define ADLB_IS_ERROR(rc) (rc == ADLB_ERROR)

// printf-like macro for printing error info
#define ERR_PRINTF(args...) printf(args)

#ifndef NDEBUG

/**
  Asserts that condition is true, else returns given error code.
  Note: This is disabled if NDEBUG is defined
*/
#define CHECK_MSG(rc, args...)                  \
  { if (!(rc)) {                                             \
      ERR_PRINTF("CHECK FAILED: %s:%i\n", __FILE__, __LINE__);   \
      ERR_PRINTF(args);                                          \
      ERR_PRINTF("\n");                                          \
      return ADLB_ERROR; }}

/**
   Checks that an MPI return code is MPI_SUCCESS
   Note: This is disabled if NDEBUG is defined
 */
#define MPI_CHECK(rc)  \
  { if (rc != MPI_SUCCESS) { \
    ERR_PRINTF("MPI_CHECK FAILED: %s:%i\n", __FILE__, __LINE__);\
    return ADLB_ERROR; }}

/**
   Checks that an ADLB return code is not ADLB_ERROR
   Note: This is disabled if NDEBUG is defined
   If used in nested functions that all return adlb_code, can
   create something like a stack trace
 */
#define ADLB_CHECK(rc) { \
  if (ADLB_IS_ERROR(rc)) { \
    ERR_PRINTF("ADLB_CHECK FAILED: %s:%s():%i\n", \
           __FILE__, __func__, __LINE__); \
    return rc; }}

/**
   Checks that an ADLB data return code is ADLB_DATA_SUCCESS
   Note: This is disabled if NDEBUG is defined
 */
#define ADLB_DATA_CHECK(dc) { \
  if (dc != ADLB_DATA_SUCCESS) { \
    ERR_PRINTF("ADLB_DATA_CHECK FAILED: %s:%i\n", __FILE__, __LINE__); \
    return ADLB_ERROR; }}

#else

// Make these noops for performance
#define CHECK_MSG(rc, args...) { (void) (rc); }
#define MPI_CHECK(rc)          { (void) (rc); }
#define ADLB_CHECK(rc)         { (void) (rc); }
#define ADLB_DATA_CHECK(rc)    { (void) (rc); }
#endif

#endif
