#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <tcl.h>

#include "uts.h"
#include "uts_inline.h"
#include "uts_leaf.h"

// TODO: inline functions here?

// Max node count before returning
#define MAX_NODE_RETURN_THRESHOLD (10 * 1024)

// Max nodes we'll hold in memory before returning
#define NODE_ARRAY_SIZE (MAX_NODE_RETURN_THRESHOLD + MAXNUMCHILDREN)

#define COMPUTE_GRANULARITY 1

typedef struct {
  tree_t tree_type;
  geoshape_t geoshape;
  int gen_mx;
  double shift_depth;
} uts_params;

// Side of node struct representation in bytes
int uts_node_strlen(void)
{
  // Encode each byte as two non-zero string bytes
  return (int)sizeof(struct node_t) * 2;
}

// Convert to string
// String must be at least uts_node_strlen long
void uts_node_tostr(char *out, const struct node_t *node)
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
void uts_str_tonode(struct node_t *out, const char *node_str, int node_strlen)
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
  child->type = uts_child_type_inline(params.tree_type,
      params.shift_depth, params.gen_mx, parent_height);
}

/*
 * Perform step doing bfs tranversal
 * nodes_size: output, number of valid nodes in nodes
 */
static bool uts_step_bfs(struct node_t *init_node,
    uts_params params, int max_nodes, int max_steps,
    int *head, int *tail)
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
    int num_children = uts_numChildren(curr);
    assert(num_children <= MAXNUMCHILDREN);

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

  // Successful exit
  *head = h;
  *tail = (h + n) % NODE_ARRAY_SIZE;
  return true;
}

/*
 * Perform step doing dfs tranversal
 * nodes_size: output, number of valid nodes in nodes
 */
static bool uts_step_dfs(struct node_t *init_node, uts_params params,
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
    int num_children = uts_numChildren(curr);
    assert(num_children <= MAXNUMCHILDREN);

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

  // Successful exit
  *nodes_size = n;
  return true;
}

#define UTS_NAMESPACE "uts::"
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, UTS_NAMESPACE tcl_function, c_function, \
                         NULL, NULL);

#define CHECK(cond, msg) \
  if (!(cond)) { fprintf(stderr, msg "\n"); return TCL_ERROR; }

static int tcl_node_string(const struct node_t *node, Tcl_Obj **string)
{
  int rc;
  *string = Tcl_NewObj();
  // expand to node cstring length, print
  int len = uts_node_strlen();
  rc = Tcl_AttemptSetObjLength(*string, len);
  CHECK(rc == 1, "Could not expand obj");
  uts_node_tostr((*string)->bytes, node);
  (*string)->bytes[len] = '\0';

  return TCL_OK;
}

/*
 * uts::uts_root <tree type> <root id>
 *
 */
static int
uts_root_cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int rc;
  int tree_type;
  int root_id;
  CHECK(objc == 3, "expected 2 args");

  rc = Tcl_GetIntFromObj(interp, objv[1], &tree_type);
  CHECK(rc == TCL_OK, "bad tree_type");
  
  rc = Tcl_GetIntFromObj(interp, objv[2], &root_id);
  CHECK(rc == TCL_OK, "bad root_id");

  Node root;
  uts_init_root(&root, tree_type, root_id);

  Tcl_Obj *node_string;
  rc = tcl_node_string(&root, &node_string);
  CHECK(rc == TCL_OK, "error copying to string");

    
  Tcl_SetObjResult(interp, node_string);
  return TCL_OK;
}

/*
 * uts::uts_run <node string> <tree_type> <geoshape> <gen_mx> <shift_depth>
                <max_nodes> <max_steps>
 */
static int
uts_run_impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool breadth_first)
{
  int rc;
  int tree_type;
  int geoshape;
  int gen_mx;
  double shift_depth;
  int max_nodes;
  int max_steps;
  CHECK(objc == 8, "expected 7 args");

  int node_string_len;
  const char *node_string = Tcl_GetStringFromObj(objv[1], &node_string_len);

  rc = Tcl_GetIntFromObj(interp, objv[2], &tree_type);
  CHECK(rc == TCL_OK, "bad tree_type");
  
  rc = Tcl_GetIntFromObj(interp, objv[3], &geoshape);
  CHECK(rc == TCL_OK, "bad geoshape");
  
  rc = Tcl_GetIntFromObj(interp, objv[4], &gen_mx);
  CHECK(rc == TCL_OK, "bad gen_mx");
  
  rc = Tcl_GetDoubleFromObj(interp, objv[5], &shift_depth);
  CHECK(rc == TCL_OK, "bad shift_depth");
  
  rc = Tcl_GetIntFromObj(interp, objv[6], &max_nodes);
  CHECK(rc == TCL_OK, "bad max_nodes");
  
  rc = Tcl_GetIntFromObj(interp, objv[7], &max_steps);
  CHECK(rc == TCL_OK, "bad max_steps");
 
  struct node_t node;
  uts_str_tonode(&node, node_string, node_string_len);

  uts_params params = {
    .tree_type = tree_type,
    .geoshape = geoshape,
    .gen_mx = gen_mx,
    .shift_depth = shift_depth,
  };


  int result_node_count;
  int result_node_head = 0, result_node_tail = 0; // for bfs

  //printf("STARTING STEP BFS %i height: %i\n",
  //      (int)breadth_first, node.height);
  if (breadth_first)
  {
    bool ok = uts_step_bfs(&node, params, max_nodes, max_steps,
                           &result_node_head, &result_node_tail);

    if (result_node_tail > result_node_head)
    {
      result_node_count = result_node_tail - result_node_head;
    }
    else
    {
      result_node_count = result_node_tail + (NODE_ARRAY_SIZE - result_node_head);
    }
    CHECK(ok, "Error in step");
  }
  else
  {
    bool ok = uts_step_dfs(&node, params, max_nodes, max_steps, &result_node_count);
    CHECK(ok, "Error in step");
  }

  Tcl_Obj *node_objs[result_node_count];
  for (int i = 0; i < result_node_count; i++)
  {
    struct node_t *result_node;
    if (breadth_first)
    {
      result_node = &nodes[(result_node_head + i) % NODE_ARRAY_SIZE];
    }
    else
    {
      result_node = &nodes[i];
    }
    rc = tcl_node_string(result_node, &node_objs[i]);
    CHECK(rc == TCL_OK, "error with node string");
  }
  //printf("FINISHED STEP BFS %i height: %i results: %i\n",
  //      (int)breadth_first, node.height, result_node_count);
  
  Tcl_Obj *node_list = Tcl_NewListObj(result_node_count, node_objs);
  Tcl_SetObjResult(interp, node_list);
  return TCL_OK;
}

static int
uts_run_bfs(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return uts_run_impl(cdata, interp, objc, objv, true);
}

static int
uts_run_dfs(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return uts_run_impl(cdata, interp, objc, objv, false);
}

int DLLEXPORT
uts_leaf_init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "uts", "0.0") == TCL_ERROR)
    return TCL_ERROR;


  COMMAND("uts_root", uts_root_cmd);
  COMMAND("uts_run_bfs", uts_run_bfs);
  COMMAND("uts_run_dfs", uts_run_dfs);
  return TCL_OK;
}

