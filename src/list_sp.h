
/**
   Extremely simple singly-linked of keyed data items
 */

#ifndef LIST_SP_H
#define LIST_SP_H

#include <stdbool.h>
#include <stddef.h>

struct list_sp_item
{
  const char* key;
  const void* data;
  struct list_sp_item* next;
};

struct list_sp
{
  struct list_sp_item* head;
  struct list_sp_item* tail;
  int size;
};

struct list_sp* list_sp_create(void);

struct list_sp_item* list_sp_add(struct list_sp* target,
                                 const char* key, const void* data);

bool list_sp_set(struct list_sp* target, const char* key,
                 const void* value, void** old_value);

bool list_sp_remove(struct list_sp* target, const char* key,
                    void** data);

void list_sp_free(struct list_sp* target);

void list_sp_destroy(struct list_sp* target);

void list_sp_dump(const char* format, const struct list_sp* target);

void list_sp_dumpkeys(const struct list_sp* target);

int list_sp_keys_string_length(const struct list_sp* target);

int list_sp_keys_tostring(char* result, const struct list_sp* target);

int list_sp_tostring(char* str, size_t size,
                     const char* format,
                     const struct list_sp* target);

#endif
