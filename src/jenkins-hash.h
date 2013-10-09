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
#ifndef __JENKINS_HASH__
#define __JENKINS_HASH__

#include <sys/types.h>
#include <stdint.h>

/* This header declares and exports Bob Jenkins hash functions define in lookup3.c.
 * lookup3.c was downloaded from:  http://www.burtleburtle.net/bob/c/lookup3.c
 * See: http://www.burtleburtle.net/bob/hash/doobs.html for more information.
 *
 * Export other functions in that library as necessary.
 */

void bj_hashlittle2(
  const void *key,       /* the key to hash */
  size_t      length,    /* length of the key */
  uint32_t   *pc,        /* IN: primary initval, OUT: primary hash */
  uint32_t   *pb);        /* IN: secondary initval, OUT: secondary hash */

uint32_t bj_hashlittle(
  const void *key,       /* the key to hash */
  size_t      length,    /* length of the key */
  uint32_t    initval);  /* Initial val */

/* bj_hashsize(shift) gives a hash table size that is a power of 2, good for bj_hashlittle2.
 * shift values are:
 *
 * 1024 -> 10
 * 4096 -> 12
 * 16384 -> 14
 * 65536 -> 16
 * 262144 -> 18
 * 1048576 -> 20
 */
#define bj_hashsize(n) ((uint32_t)1<<(n))

/* bj_hashmask(n) gives a mask reasonable for returning a hash between 0 and the table size.
 */
#define bj_hashmask(n) (bj_hashsize(n)-1)

#endif
