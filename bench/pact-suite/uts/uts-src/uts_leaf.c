#include <assert.h>
#include <stdbool.h>
#include <stdint.h>

#include "uts.h"
#include "uts_inline.h"

// TODO: inline functions here?

// Max stack size before returning
#define STACK_RETURN_THRESHOLD 1024

// Max nodes to process before returning
#define MAX_NODES_PROCESSED (1024 * 1024)

// Max nodes we'll hold in memory before returning
#define MAX_STACK_SIZE (STACK_RETURN_THRESHOLD + MAXNUMCHILDREN)

#define COMPUTE_GRANULARITY 1

static struct node_t node_stack[MAX_STACK_SIZE];

// TODO: Tcl wrapper function

/*
 * Perform step
 * TODO: dfs versus bfs
 * node_stack_size: output, number of valid nodes in node_stack
 */
static bool uts_step(struct node_t *init_node,
                    int *node_stack_size)
{
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
      struct node_t *child = &node_stack[n++];
      for (int j = 0; j < COMPUTE_GRANULARITY; j++)
      {
        rng_spawn(state.state, child->state.state, i);
      }
      child->height = height + 1;
      child->type = uts_child_type_inline(height);
    }

    processed++;

    // Pop next node off stack to process
    n--;
    curr = &node_stack[n]; // Invalid if n < 0
  }
  while (n >= 0 && n < STACK_RETURN_THRESHOLD &&
         processed < MAX_NODES_PROCESSED);

  // Successful exit
  *node_stack_size = n;
  return true;
}
