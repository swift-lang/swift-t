#include "data_cleanup.h"

#include "data_structs.h"
#include "debug.h"
#include "multiset.h"
#include "refcount.h"
#include "table_bp.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

adlb_data_code
xlb_datum_cleanup(adlb_datum_storage *d,
     adlb_data_type type, bool free_mem,
     bool release_read, bool release_write,
     xlb_refc_acquire to_acquire, xlb_refc_changes *refcs)
{
  // Sanity-check acquire amounts
  assert(to_acquire.refcounts.read_refcount >= 0);
  assert(to_acquire.refcounts.write_refcount >= 0);
  adlb_data_code dc;
  
  if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    // Optimization: do single pass over container for freeing memory and
    // reference counting
    return xlb_members_cleanup(&d->CONTAINER, free_mem, release_read,
            release_write, to_acquire, refcs);
  }
  else if (type == ADLB_DATA_TYPE_MULTISET)
  {
    return xlb_multiset_cleanup(d->MULTISET, free_mem, free_mem, release_read,
            release_write, to_acquire, refcs);
  }
  else if (type == ADLB_DATA_TYPE_STRUCT)
  {
    return xlb_struct_cleanup(d->STRUCT, free_mem, release_read,
              release_write, to_acquire,
              refcs);
  }
  else
  {
    if (release_read || release_write ||
        !ADLB_REFC_IS_NULL(to_acquire.refcounts))
    {
      // Decrement any reference counts required
      assert(!adlb_has_sub(to_acquire.subscript));
      dc = xlb_incr_referand(d, type, release_read, release_write,
                             to_acquire, refcs);
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
  refcs: changes resulting from refcount manipulation.
        Not touched if release_read == released_write == false and
        to_acquire is (0, 0), so can be NULL in that case
 */
adlb_data_code
xlb_members_cleanup(adlb_container *container, bool free_mem,
  bool release_read, bool release_write, xlb_refc_acquire to_acquire,
  xlb_refc_changes *refcs)
{
  adlb_data_code dc;
  struct table_bp* members = container->members;

  // Whether we are making any refcount changes
  bool refcount_change = release_read || release_write ||
                          !ADLB_REFC_IS_NULL(to_acquire.refcounts);

  TRACE("Freeing container %p", container);
  for (int i = 0; i < members->capacity; i++)
  {
    table_bp_entry* head = &members->array[i];
    if (!table_bp_entry_valid(head))
    {
      // Empty bucket
      continue;
    }

    // Keep next pointer to allow freeing of item
    table_bp_entry* item, *next;
    for (item = head, next = head->next; item != NULL;
         item = next)
    {
      next = item->next; // Store next pointer immediately

      adlb_datum_storage *d = (adlb_datum_storage*)item->data;
      
      TRACE("Freeing %p in %p", d, container);
      // Value may be null when insert_atomic occurred, but nothing inserted
      if (refcount_change && d != NULL)
      {
        adlb_subscript *sub = &to_acquire.subscript;
        bool acquire_field = (!adlb_has_sub(*sub) ||
             table_bp_key_match(sub->key, sub->length, item));

        // create new acquire to remove subscript
        xlb_refc_acquire field_acquire;
        field_acquire.subscript = ADLB_NO_SUB;
        if (acquire_field)
        {
          field_acquire.refcounts = to_acquire.refcounts;
        }
        else
        {
          field_acquire.refcounts = ADLB_NO_REFC;
        }

        dc = xlb_incr_referand(d, (adlb_data_type)container->val_type,
                release_read, release_write, field_acquire, refcs);
        DATA_CHECK(dc);
      }
      // Free the memory for value and key
      if (free_mem)
      {
        if (d != NULL)
        {
          dc = ADLB_Free_storage(d, (adlb_data_type)container->val_type);
          DATA_CHECK(dc);
          free(d);
        }
      }
      // Free list node and move to next
      if (free_mem)
        table_bp_free_entry(item, item == head);
    }


    // Mark bucket empty
    if (free_mem)
      table_bp_clear_entry(head);
  }
  if (free_mem)
    table_bp_free(members);
  return ADLB_DATA_SUCCESS;
}


