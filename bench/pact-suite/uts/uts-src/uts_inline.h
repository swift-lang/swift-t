#ifndef __UTS_INLINE
#define __UTS_INLINE

#include "uts.h"

static inline void uts_init_root(Node * root, tree_t type, int root_id) {
  root->type = type;
  root->height = 0;
  root->numChildren = -1;      // means not yet determined
  rng_init(root->state.state, root_id);
}

static inline int uts_child_type_inline(tree_t tree_type,
      int shift_depth, int gen_mx, int parent_height) {
  switch (tree_type) {
    case BIN:
      return BIN;
    case GEO:
      return GEO;
    case HYBRID:
      if (parent_height < shift_depth * gen_mx)
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

#endif // __UTS_INLINE
