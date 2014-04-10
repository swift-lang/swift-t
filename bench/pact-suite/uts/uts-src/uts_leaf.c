#include <assert.h>
#include <tcl.h>

#include "uts.h"
#include "uts_inline.h"
#include "uts_leaf.h"

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
  double b_0;
  int geoshape;
  int gen_mx;
  double shift_depth;
  int max_nodes;
  int max_steps;
  CHECK(objc == 9, "expected 8 args");

  int node_string_len;
  const char *node_string = Tcl_GetStringFromObj(objv[1], &node_string_len);

  rc = Tcl_GetIntFromObj(interp, objv[2], &tree_type);
  CHECK(rc == TCL_OK, "bad tree_type");
  
  rc = Tcl_GetDoubleFromObj(interp, objv[3], &b_0);
  CHECK(rc == TCL_OK, "bad b_0");

  rc = Tcl_GetIntFromObj(interp, objv[4], &geoshape);
  CHECK(rc == TCL_OK, "bad geoshape");
  
  rc = Tcl_GetIntFromObj(interp, objv[5], &gen_mx);
  CHECK(rc == TCL_OK, "bad gen_mx");
  
  rc = Tcl_GetDoubleFromObj(interp, objv[6], &shift_depth);
  CHECK(rc == TCL_OK, "bad shift_depth");
  
  rc = Tcl_GetIntFromObj(interp, objv[7], &max_nodes);
  CHECK(rc == TCL_OK, "bad max_nodes");
  
  rc = Tcl_GetIntFromObj(interp, objv[8], &max_steps);
  CHECK(rc == TCL_OK, "bad max_steps");
 
  struct node_t node;
  uts_str_tonode(&node, node_string, node_string_len);

  uts_params params = {
    .tree_type = tree_type,
    .b_0 = b_0,
    .geoshape = geoshape,
    .gen_mx = gen_mx,
    .shift_depth = shift_depth,
  };


  int result_node_count;
  int result_node_head = 0, result_node_tail = 0; // for bfs

  int before_nodes_processed = total_nodes_processed;

  UTS_TRACE("STARTING STEP BFS %i height: %i\n",
        (int)breadth_first, node.height);
  if (breadth_first)
  {
    bool ok = uts_step_bfs(&node, params, max_nodes, max_steps,
                           &result_node_head, &result_node_tail, &result_node_count);

    UTS_TRACE("h: %i t: %i n: %i\n", result_node_head, result_node_tail, result_node_count);
    CHECK(ok, "Error in step");
  }
  else
  {
    bool ok = uts_step_dfs(&node, params, max_nodes, max_steps, &result_node_count);
    CHECK(ok, "Error in step");
  }

  int report_interval = 500000;
  if (total_nodes_processed / report_interval >
      before_nodes_processed / report_interval )
  {
    UTS_INFO("Processed %ld nodes\n", total_nodes_processed);
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
  UTS_DEBUG("FINISHED STEP BFS %i height: %i results: %i\n",
        (int)breadth_first, node.height, result_node_count);
  
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

