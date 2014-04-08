/*
 * Unbalanced Tree Search 
 *
 * sha1.h
 *   SHA-1 cryptographic hash splittable random number
 *   generator for UTS
 *
 */
#ifndef _SHA1_H
#define _SHA1_H

/* the following may need to be adjusted on some
 * 64 bit architectures
 * TODO:  use ansi definitions
 */
#define uint8  unsigned char
#define uint32 unsigned long int
#define uint64 unsigned long long int

#define POS_MASK    0x7fffffff
#define HIGH_BITS   0x80000000

#define RNG_state uint8

/**********************************/
/* random number generator state  */
/**********************************/
struct state_t {
  uint8 state[20];
};


/***************************************/
/* random number generator operations  */
/***************************************/
void rng_init(RNG_state *state, int seed);
void rng_spawn(RNG_state *mystate, RNG_state *newstate, int spawnNumber);
int rng_rand(RNG_state *mystate);
int rng_nextrand(RNG_state *mystate);
char * rng_showstate(RNG_state *state, char *s);
int rng_showtype(char *strBuf, int ind);


/* below here:  used by sha1.c
 */
struct sha1_context
{
    uint64 total;
    uint32 state[5];
    uint8 buffer[64];
};

void sha1_starts( struct sha1_context *ctx );
void sha1_update( struct sha1_context *ctx, uint8 *input, uint32 length );
void sha1_finish( struct sha1_context *ctx, uint8 digest[20] );

#endif /* sha1.h */

