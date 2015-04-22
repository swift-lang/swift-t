#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include <sys/time.h>
#ifdef sgi
#include <time.h>
#else
#include <sys/time.h>
#endif

#ifdef __MTA__
#include <sys/mta_task.h>
#include <machine/mtaops.h>
#include <machine/runtime.h>
#endif /* __MTA__ */

#include "uts.h"

/***********************************************************
 *  tree generation and search parameters                  *
 *                                                         *
 *  Tree generation strategy is controlled via various     *
 *  parameters set from the command line.  The parameters  *
 *  and their default values are given below.              *
 ***********************************************************/

char * uts_trees_str[]     = { "Binomial", "Geometric", "Hybrid", "Balanced" };
char * uts_geoshapes_str[] = { "Linear decrease", "Exponential decrease", "Cyclic", 
                              "Fixed branching factor" };

/* Tree type
 *   Trees are generated using a Galton-Watson process, in 
 *   which the branching factor of each node is a random 
 *   variable.
 *   
 *   The random variable can follow a binomial distribution
 *   or a geometric distribution.  Hybrid tree are
 *   generated with geometric distributions near the
 *   root and binomial distributions towards the leaves.
 */
tree_t type  = GEO; // Default tree type
double b_0   = 4.0; // default branching factor at the root
int   rootId = 0;   // default seed for RNG state at root

/*  Tree type BIN (BINOMIAL)
 *  The branching factor at the root is specified by b_0.
 *  The branching factor below the root follows an 
 *     identical binomial distribution at all nodes.
 *  A node has m children with prob q, or no children with 
 *     prob (1-q).  The expected branching factor is q * m.
 *
 *  Default parameter values 
 */
int    nonLeafBF   = 4;            // m
double nonLeafProb = 15.0 / 64.0;  // q

/*  Tree type GEO (GEOMETRIC)
 *  The branching factor follows a geometric distribution with
 *     expected value b.
 *  The probability that a node has 0 <= n children is p(1-p)^n for
 *     0 < p <= 1. The distribution is truncated at MAXNUMCHILDREN.
 *  The expected number of children b = (1-p)/p.  Given b (the
 *     target branching factor) we can solve for p.
 *
 *  A shape function computes a target branching factor b_i
 *     for nodes at depth i as a function of the root branching
 *     factor b_0 and a maximum depth gen_mx.
 *   
 *  Default parameter values
 */
int        gen_mx   = 6;      // default depth of tree
geoshape_t shape_fn = LINEAR; // default shape function (b_i decr linearly)

/*  In type HYBRID trees, each node is either type BIN or type
 *  GEO, with the generation strategy changing from GEO to BIN 
 *  at a fixed depth, expressed as a fraction of gen_mx
 */
double shiftDepth = 0.5;         

/* compute granularity - number of rng evaluations per tree node */
int computeGranularity = 1;

/* display parameters */
int debug    = 0;
int verbose  = 1;


/***********************************************************
 *                                                         *
 *  FUNCTIONS                                              *
 *                                                         *
 ***********************************************************/

/* fatal error */
void uts_error(char *str) {
  printf("*** Error: %s\n", str);
  exit(1);
}

/*
 * wall clock time
 *   for detailed accounting of work, this needs
 *   high resolution
 */
#ifdef sgi  
double uts_wctime() {
  timespec_t tv;
  double time;
  clock_gettime(CLOCK_SGI_CYCLE,&tv);
  time = ((double) tv.tv_sec) + ((double)tv.tv_nsec / 1e9);
  return time;
}
#elif defined  __MTA__
double uts_wctime() {
  return (double) mta_get_clock(0)/mta_clock_freq();
}
#else
double uts_wctime() {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return (tv.tv_sec + 1E-6 * tv.tv_usec);
}
#endif


// Interpret 32 bit positive integer as value on [0,1)
double rng_toProb(int n) {
  if (n < 0) {
    printf("*** toProb: rand n = %d out of range\n",n);
  }
  return ((n<0)? 0.0 : ((double) n)/2147483648.0);
}


void uts_initRoot(Node * root, int type) {
  root->type = type;
  root->height = 0;
  root->numChildren = -1;      // means not yet determined
  rng_init(root->state.state, rootId);

  if (debug & 1)
    printf("root node of type %d at %p\n",type, root);
}


int uts_numChildren_bin(Node * parent) {
  // distribution is identical everywhere below root
  int    v = rng_rand(parent->state.state);	
  double d = rng_toProb(v);

  return (d < nonLeafProb) ? nonLeafBF : 0;
}


int uts_numChildren_geo(Node * parent) {
  double b_i = b_0;
  int depth = parent->height;
  int numChildren, h;
  double p, u;
  
  // use shape function to compute target b_i
  if (depth > 0){
    switch (shape_fn) {
      
      // expected size polynomial in depth
    case EXPDEC:
      b_i = b_0 * pow((double) depth, -log(b_0)/log((double) gen_mx));
      break;
      
      // cyclic tree size
    case CYCLIC:
      if (depth > 5 * gen_mx){
        b_i = 0.0;
        break;
      } 
      b_i = pow(b_0, 
                sin(2.0*3.141592653589793*(double) depth / (double) gen_mx));
      break;

      // identical distribution at all nodes up to max depth
    case FIXED:
      b_i = (depth < gen_mx)? b_0 : 0;
      break;
      
      // linear decrease in b_i
    case LINEAR:
    default:
      b_i =  b_0 * (1.0 - (double)depth / (double) gen_mx);
      break;
    }
  }

  // given target b_i, find prob p so expected value of 
  // geometric distribution is b_i.
  p = 1.0 / (1.0 + b_i);

  // get uniform random number on [0,1)
  h = rng_rand(parent->state.state);
  u = rng_toProb(h);

  // max number of children at this cumulative probability
  // (from inverse geometric cumulative density function)
  numChildren = (int) floor(log(1 - u) / log(1 - p)); 

  return numChildren;
}


int uts_numChildren(Node *parent) {
  int numChildren = 0;

  /* Determine the number of children */
  switch (type) {
    case BIN:
      if (parent->height == 0)
        numChildren = (int) floor(b_0);
      else 
        numChildren = uts_numChildren_bin(parent);
      break;
  
    case GEO:
      numChildren = uts_numChildren_geo(parent);
      break;
    
    case HYBRID:
      if (parent->height < shiftDepth * gen_mx)
        numChildren = uts_numChildren_geo(parent);
      else
        numChildren = uts_numChildren_bin(parent);
      break;
    case BALANCED:
      if (parent->height < gen_mx)
        numChildren = (int) b_0;
      break;
    default:
      uts_error("parTreeSearch(): Unknown tree type");
  }
  
  // limit number of children
  // only a BIN root can have more than MAXNUMCHILDREN
  if (parent->height == 0 && parent->type == BIN) {
    int rootBF = (int) ceil(b_0);
    if (numChildren > rootBF) {
      printf("*** Number of children of root truncated from %d to %d\n",
             numChildren, rootBF);
      numChildren = rootBF;
    }
  }
  else if (type != BALANCED) {
    if (numChildren > MAXNUMCHILDREN) {
      printf("*** Number of children truncated from %d to %d\n", 
             numChildren, MAXNUMCHILDREN);
      numChildren = MAXNUMCHILDREN;
    }
  }

  return numChildren;
}


int uts_childType(Node *parent) {
  switch (type) {
    case BIN:
      return BIN;
    case GEO:
      return GEO;
    case HYBRID:
      if (parent->height < shiftDepth * gen_mx)
        return GEO;
      else 
        return BIN;
    case BALANCED:
      return BALANCED;
    default:
      uts_error("uts_get_childtype(): Unknown tree type");
      return -1;
  }
}




void uts_showStats(int nPes, int chunkSize, double walltime, counter_t nNodes, counter_t nLeaves, counter_t maxDepth) {
  // summarize execution info for machine consumption
  if (verbose == 0) {
    printf("%4d %7.3f %9llu %7.0llu %7.0llu %d %d %.2f %d %d %1d %f %3d\n",
        nPes, walltime, nNodes, (long long)(nNodes/walltime), (long long)((nNodes/walltime)/nPes), chunkSize, 
        type, b_0, rootId, gen_mx, shape_fn, nonLeafProb, nonLeafBF);
  }

  // summarize execution info for human consumption
  else {
    printf("Tree size = %llu, tree depth = %llu, num leaves = %llu (%.2f%%)\n", nNodes, maxDepth, nLeaves, nLeaves/(float)nNodes*100.0); 
    printf("Wallclock time = %.3f sec, performance = %.0f nodes/sec (%.0f nodes/sec per PE)\n\n",
        walltime, (nNodes / walltime), (nNodes / walltime / nPes));
  }
}
