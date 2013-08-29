#include "data_cleanup.h"

#include "data_structs.h"
#include "multiset.h"
#include "refcount.h"
#include "table.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

adlb_data_code
cleanup_storage(adlb_datum_storage *d, adlb_data_type type, adlb_datum_id id,
              refcount_scavenge scav)
{
  // Can't scavenge more than one since we don't hold more than one
  // refcount per referenced item
  assert(scav.refcounts.read_refcount == 0 ||
         scav.refcounts.read_refcount == 1);
  // TODO: don't currently hold write references internally
  assert(scav.refcounts.write_refcount == 0);
  adlb_refcounts rc_change = { .read_refcount = -1, .write_refcount = 0 };
  return cleanup_storage2(d, type, id, true, rc_change, scav);
}

adlb_data_code
cleanup_storage2(adlb_datum_storage *d, adlb_data_type type,
             adlb_datum_id id, bool free_mem,
             adlb_refcounts rc_change, refcount_scavenge scav)
{
  adlb_data_code dc;
  
  if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    // Optimization: do single pass over container for freeing memory and
    // reference counting
    return cleanup_members(&d->CONTAINER, free_mem, rc_change, scav);
  }
  else if (type == ADLB_DATA_TYPE_MULTISET)
  {
    return xlb_multiset_cleanup(d->MULTISET, free_mem, free_mem,
                                rc_change, scav);
  }
  else if (type == ADLB_DATA_TYPE_STRUCT)
  {
    int scav_ix = -1; // negative == don't scavenge
    if (scav.subscript != NULL) 
    {
      dc = data_struct_str_to_ix(scav.subscript, &scav_ix);
      DATA_CHECK(dc);
    }
    return data_cleanup_struct(d->STRUCT, free_mem, rc_change,
                                     scav.refcounts, scav_ix);
  }
  else
  {
    if (!ADLB_RC_IS_NULL(rc_change))
    {
      // Decrement any reference counts required
      assert(scav.subscript == NULL);
      dc = incr_scav_rc_referand(d, type, rc_change, scav.refcounts);
      DATA_CHECK(dc);
    }

    if (free_mem)
    {
      // Then free memory
      dc = ADLB_Free_storage(d, type);
      DATA_CHECK(dc);
    }
    return ADLB_DATA_SUCCESS;
  }
}

/*
  Free memory held by container data structure
  Decrement reference counts for other data if required
 */
adlb_data_code
cleanup_members(adlb_container *container, bool free_mem,
                  adlb_refcounts rc_change, refcount_scavenge scav)
{
  adlb_data_code dc;
  struct table* members = container->members;
  
  for (int i = 0; i < members->capacity; i++)
  {
    struct list_sp* L = members->array[i];
    struct list_sp_item* item = L->head;
    while (item != NULL)
    {
      adlb_datum_storage *d = (adlb_datum_storage*)item->data;
      
      // Value may be null when insert_atomic occurred, but nothing inserted
      if (!ADLB_RC_IS_NULL(rc_change) && d != NULL)
      {
        bool do_scavenge = !ADLB_RC_IS_NULL(scav.refcounts) && 
           (scav.subscript == NULL || strcmp(scav.subscript, item->key) == 0);

        if (do_scavenge)
        {
          // Note: if we're scavenging entire container, refcounts_scavenged
          // will be overwritten many times, but will have correct result
          dc = incr_scav_rc_referand(d, container->val_type,
                                     rc_change, scav.refcounts);
          DATA_CHECK(dc);
        }
        else
        {
          dc = incr_rc_referand(d, container->val_type, rc_change);
          DATA_CHECK(dc);
        }
      }

      // Free the memory for value and key
      if (free_mem)
      {
        if (d != NULL)
        {
          dc = ADLB_Free_storage(d, container->val_type);
          DATA_CHECK(dc);
          free(d);
        }
        assert(item->key != NULL);
        free(item->key);
      }
     
      // Free list node and move to next
      struct list_sp_item* prev_item = item;
      item = item->next;
      if (free_mem)
        free(prev_item);
    }
    if (free_mem)
    {
      members->array[i]->head = NULL;
      members->array[i]->size = 0;
    }
  }
  if (free_mem)
    table_free(members);
  return ADLB_DATA_SUCCESS;
}


