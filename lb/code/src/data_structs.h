/*
  Support for struct data type in ADLB
 */

#ifndef __XLB_DATA_STRUCTS_H
#define __XLB_DATA_STRUCTS_H

#include "adlb-defs.h"
#include "adlb_types.h"
#include "data.h"
#include "notifications.h"

/*
  Type of a struct field.
  Need extra type info because we might need to initialize the data
  item, e.g. if a subscript of a field is assigned.  If extra type
  info is required to initialize the type, and it is not provided,
  any operation that requires initializing the field will fail.
  Currently the extra type info is only used to initialize fields:
  it is not used to type-check assignments to fields.
 */
typedef struct {
  bool initialized;
  char *type_name;
  int field_count;
  char **field_names;
  adlb_struct_field_type *field_types;
} xlb_struct_type_info;

adlb_data_code xlb_new_struct(adlb_struct_type type, adlb_struct **s);

// Free all memory allocated within this module
adlb_data_code xlb_struct_finalize(void);

// Return the name of a declared struct type
const char *xlb_struct_type_name(adlb_struct_type type);

// Return info about the struct type
// return: pointer to struct type info, valid until finalize called.
//         NULL if invalid type
const xlb_struct_type_info *
xlb_get_struct_type_info(adlb_struct_type type);

// Free memory associated with struct, including the
// provided pointer if specified.  If recurse specified, free
// struct fields too
adlb_data_code xlb_free_struct(adlb_struct *s, bool free_root_ptr,
                               bool recurse);

/**
 * Search for subscript in struct, consuming as much of subscript as
 * we can by traversing nested structs.
 *
 * In the case that we encounter an uninitialized inner struct, we
 * return that field
 *
 * init_nested: if true, initialize inner structs on path
 * field: field of struct located
 * type: type info for field returned
 * sub_pos: index of start of remainder of subscript
 * return: ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND if could not be found
 */
adlb_data_code xlb_struct_lookup(adlb_struct *s, adlb_subscript sub,
                    bool init_nested,
                    adlb_struct_field **field, adlb_struct_field_type *type,
                    size_t *sub_pos);

/*
 * Get data for struct field.
 * Returns error if field invalid.
 * val: result if field is set, NULL if not yet set
 * type: type of field (always set)
 */
adlb_data_code xlb_struct_get_field(adlb_struct *s, int field_ix,
                        const adlb_datum_storage **val, adlb_data_type *type);

adlb_data_code xlb_struct_get_subscript(adlb_struct *s, adlb_subscript subscript,
                        const adlb_datum_storage **val, adlb_data_type *type);

/*
 * Check if a subscript of a struct is initialized.
 * validate_path: if false, an invalid path may result in an error
 *    return code, or simply say that the subscript is not init
 *    if true, an invalid path always causes an error
 */
adlb_data_code xlb_struct_subscript_init(adlb_struct *s, adlb_subscript subscript,
                                        bool validate_path, bool *b);

adlb_data_code xlb_struct_assign_field(adlb_struct_field *field,
        adlb_struct_field_type field_type, const void *data, size_t length,
        adlb_data_type data_type, adlb_refc refcounts);

adlb_data_code xlb_struct_set_field(adlb_struct *s, int field_ix,
                const void *data, size_t length, adlb_data_type type,
                adlb_refc refcounts);

/**
 * Set struct field at subscript
 * init_nested: if true, initialize inner structs on path
 */
adlb_data_code xlb_struct_set_subscript(adlb_struct *s,
      adlb_subscript subscript, bool init_nested,
      const void *data, size_t length, adlb_data_type type,
      adlb_refc refcounts);

adlb_data_code
xlb_struct_cleanup(adlb_struct *s, bool free_mem, bool release_read,
                   bool release_write, 
                   xlb_refc_acquire to_acquire,
                   xlb_refc_changes *refcs);

char *xlb_struct_repr(adlb_struct *s);

#endif // __XLB_DATA_STRUCTS_H
