/*
 * Unbalanced Tree Search 
 *
 * alfg.h
 *   Additive Lagged Fibonacci Generator
 *    - splittable pseudorandom number generator for UTS
 *
 */
#ifndef _ALFG_H
#define _ALFG_H

/*
 * ALFG parameters
 *   L choices:  17, 55, 159, 607, 1279
 *
 */
#define UTS_ALFG_L           55          /* lag distance */

/****************************/
/* state array index names: */
/****************************/
#define J_STATE_SIZE      0
#define J_L               1
#define J_SEED            2
#define J_K               3
#define J_CBIT            4
#define J_RUNUP           5
#define J_LP              6
#define J_KP             7
#define J_ZP             8
#define N_SCALARS        9
#define NODE0            N_SCALARS
#define REG0             (NODE0+l-1)

#define POS_MASK         0x7fffffff
#define HIGH_BIT         0x80000000

/**********************************/
/* random number generator state  */
/**********************************/
struct state_t {
  int state[2*UTS_ALFG_L-1+N_SCALARS];
};

#define RNG_state int

/***************************************/
/* random number generator operations  */
/***************************************/

void rng_init(RNG_state *state, int seed);
void rng_spawn(RNG_state *mystate, RNG_state *newstate, int spawnNumber);
int rng_rand(RNG_state *mystate);
int rng_nextrand(RNG_state *mystate);
char * rng_showstate(RNG_state *state, char *s);
int rng_showtype(char *strBuf, int ind);

#endif /*alfg.h */
