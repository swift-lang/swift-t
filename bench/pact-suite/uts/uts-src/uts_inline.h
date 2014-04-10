#ifndef __UTS_INLINE
#define __UTS_INLINE

#include <math.h>

#include "uts.h"

typedef struct {
  tree_t tree_type;
  geoshape_t geoshape;
  double b_0;
  int gen_mx;
  double shift_depth;
} uts_params;


static inline void uts_init_root(Node * root, tree_t type, int root_id) {
  root->type = type;
  root->height = 0;
  root->numChildren = -1;      // means not yet determined
  rng_init(root->state.state, root_id);
}

static inline int uts_child_type_inline(uts_params params, int parent_height) {
  switch (params.tree_type) {
    case BIN:
      return BIN;
    case GEO:
      return GEO;
    case HYBRID:
      if (parent_height < params.shift_depth * params.gen_mx)
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

static inline int uts_num_children_bin(Node * parent, uts_params params) {
  printf("BIN NOT IMPLEMENTED\n");
  exit(1);
}


static inline int uts_num_children_geo(Node * parent, uts_params params) {
  double b_i = params.b_0;
  int depth = parent->height;
  int numChildren, h;
  double p, u;
  
  // use shape function to compute target b_i
  if (depth > 0){
    switch (params.geoshape) {
      
      // expected size polynomial in depth
    case EXPDEC:
      b_i = b_0 * pow((double) depth, -log(params.b_0)/log((double) params.gen_mx));
      break;
      
      // cyclic tree size
    case CYCLIC:
      if (depth > 5 * params.gen_mx){
        b_i = 0.0;
        break;
      } 
      b_i = pow(params.b_0, 
                sin(2.0*3.141592653589793*(double) depth / (double) params.gen_mx));
      break;

      // identical distribution at all nodes up to max depth
    case FIXED:
      b_i = (depth < params.gen_mx)? params.b_0 : 0;
      break;
      
      // linear decrease in b_i
    case LINEAR:
    default:
      b_i =  params.b_0 * (1.0 - (double)depth / (double) params.gen_mx);
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


static inline int uts_num_children(Node *parent, uts_params params) {
  int numChildren = 0;
  tree_t type = params.tree_type;

  /* Determine the number of children */
  switch (type) {
    case BIN:
      if (parent->height == 0)
        numChildren = (int) floor(params.b_0);
      else 
        numChildren = uts_num_children_bin(parent, params);
      break;
  
    case GEO:
      numChildren = uts_num_children_geo(parent, params);
      break;
    
    case HYBRID:
      if (parent->height < params.shift_depth * params.gen_mx)
        numChildren = uts_num_children_geo(parent, params);
      else
        numChildren = uts_num_children_bin(parent, params);
      break;
    case BALANCED:
      if (parent->height < params.gen_mx)
        numChildren = (int) params.b_0;
      break;
    default:
      uts_error("parTreeSearch(): Unknown tree type");
  }
  
  // limit number of children
  // only a BIN root can have more than MAXNUMCHILDREN
  if (parent->height == 0 && parent->type == BIN) {
    int rootBF = (int) ceil(params.b_0);
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

#endif // __UTS_INLINE
