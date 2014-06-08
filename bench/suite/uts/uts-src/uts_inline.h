#ifndef __UTS_INLINE
#define __UTS_INLINE
/*
 * Common code between Tcl and non-Tcl versions
 */

#include <stdbool.h>
#include <stdint.h>
#include <math.h>

#include "uts.h"

#ifdef UTS_TRACE_ENABLED
#define UTS_TRACE(fmt, args...) printf("TRACE: " fmt, ## args)
#else
#define UTS_TRACE(fmt, args...)
#endif
#ifdef UTS_DEBUG_ENABLED
#define UTS_DEBUG(fmt, args...) printf("DEBUG: " fmt, ## args)
#else
#define UTS_DEBUG(fmt, args...)
#endif
#ifdef UTS_INFO_ENABLED
#define UTS_INFO(fmt, args...) printf("INFO: " fmt, ## args)
#else
#define UTS_INFO(fmt, args...)
#endif

// Max node count before returning
#define MAX_NODE_RETURN_THRESHOLD (10 * 1024)

// Max nodes we'll hold in memory before returning
#define NODE_ARRAY_SIZE (MAX_NODE_RETURN_THRESHOLD + MAXNUMCHILDREN)

#define COMPUTE_GRANULARITY 1

#define NODE_REPORT_INTERVAL (1024*1024)

static long int total_nodes_processed = 0;

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

// Side of node struct representation in bytes
static inline int uts_node_strlen(void)
{
  // Encode each byte as two non-zero string bytes
  return (int)sizeof(struct node_t) * 2;
}

// Convert to string
// String must be at least uts_node_strlen long
static inline void uts_node_tostr(char *out, const struct node_t *node)
{
  const char *data = (const char*)node;
  int pos = 0;
  for (int i = 0; i < sizeof(*node); i++)
  {
    out[pos++] = ((data[i] & 0xf0) >> 4) + '0';
    out[pos++] = (data[i] & 0x0f)  + '0';
  }
  out[pos++] = '\0';
}

// String must be uts_node_strlen long
static inline void uts_str_tonode(struct node_t *out, const char *node_str, int node_strlen)
{
  assert(node_strlen == uts_node_strlen());

  char *data = (char*)out;
  for (int i = 0; i < sizeof(*out); i++)
  {
    data[i] = ((node_str[2*i] - '0') << 4) + (node_str[2*i+1] - '0');
  }
}

static struct node_t nodes[NODE_ARRAY_SIZE];

static inline void uts_child(struct node_t *child, int child_ix,
                        int parent_height, int parent_type,
                        uts_params params,
                        struct state_t *parent_state)
{
  for (int i = 0; i < COMPUTE_GRANULARITY; i++)
  {
    rng_spawn(parent_state->state, child->state.state, child_ix);
  }
  child->height = parent_height + 1;
  child->type = uts_child_type_inline(params, parent_height);
}

/*
 * Perform step doing bfs tranversal
 * nodes_size: output, number of valid nodes in nodes
 */
static inline bool uts_step_bfs(struct node_t *init_node,
    uts_params params, int max_nodes, int max_steps,
    int *head, int *tail, int *count)
{
  if (max_nodes > MAX_NODE_RETURN_THRESHOLD)
  {
    max_nodes = MAX_NODE_RETURN_THRESHOLD;
  }
  int processed = 0;
  int n = 0; // Node count in queue
  int h = 0; // Queue head node

  struct node_t *curr = init_node;
  do
  {
    int height = curr->height;
    int type = curr->type;
    int num_children = uts_num_children(curr, params);
    assert(num_children <= MAXNUMCHILDREN);

    UTS_TRACE("BFS Node@height %i children %i h %i n %i\n", height, num_children, h, n);

    struct state_t state = curr->state;

    // curr is unused from here onwards, can safely clobber space in stack
    for (int i = 0; i < num_children; i++)
    {
      struct node_t *child = &nodes[(h + n) % NODE_ARRAY_SIZE];
      uts_child(child, i, height, type, params, &state);
      n++;
    }

    processed++;

    // Pop next node off queue head
    curr = &nodes[h]; // Invalid if n < 0
    h = (h + 1) % NODE_ARRAY_SIZE;
    n--;
  }
  while (n >= 0 && n < max_nodes &&
         processed < max_steps);

  total_nodes_processed += processed;

  // Successful exit
  *head = h;
  *tail = (h + n) % NODE_ARRAY_SIZE;
  *count = n;
  return true;
}

/*
 * Perform step doing dfs tranversal
 * nodes_size: output, number of valid nodes in nodes
 */
static inline bool uts_step_dfs(struct node_t *init_node, uts_params params,
    int max_nodes, int max_steps, int *nodes_size)
{
  if (max_nodes > MAX_NODE_RETURN_THRESHOLD)
  {
    max_nodes = MAX_NODE_RETURN_THRESHOLD;
  }
  int n = 0; // Node count in stack
  int processed = 0;

  struct node_t *curr = init_node;
  do
  {
    int height = curr->height;
    int type = curr->type;
    int num_children = uts_num_children(curr, params);
    assert(num_children <= MAXNUMCHILDREN);
    
    UTS_TRACE("DFS Node@height %i children %i\n", height, num_children);

    struct state_t state = curr->state;

    // curr is unused from here onwards, can safely clobber space in stack
    for (int i = 0; i < num_children; i++)
    {
      struct node_t *child = &nodes[n++];
      uts_child(child, i, height, type, params, &state);
    }

    processed++;

    // Pop next node off stack to process
    n--;
    curr = &nodes[n]; // Invalid if n < 0
  }
  while (n >= 0 && n < max_nodes &&
         processed < max_steps);
  
  total_nodes_processed += processed;

  // Successful exit
  *nodes_size = n;
  return true;
}

#endif // __UTS_INLINE
