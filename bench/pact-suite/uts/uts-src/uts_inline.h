#ifndef __UTS_INLINE
#define __UTS_INLINE

extern tree_t type;
extern double shiftDepth;
extern int gen_mx;

static inline int uts_child_type_inline(int parent_height) {
  switch (type) {
    case BIN:
      return BIN;
    case GEO:
      return GEO;
    case HYBRID:
      if (parent_height < shiftDepth * gen_mx)
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
