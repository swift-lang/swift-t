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

/* bj_hashmask(n) gives a mask reasonable for returning a hash between 0 and the hashtable size.
 */
#define bj_hashmask(n) (bj_hashsize(n)-1)

#endif
