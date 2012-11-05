
/*
 * tree-common.h
 *
 *  Created on: Nov 5, 2012
 *      Author: wozniak
 *
 *  Some things shared by tree.c and rbtree.c
 */

#ifndef TREE_COMMON_H
#define TREE_COMMON_H

typedef enum
{
  LEFT, RIGHT, ROOT, NEITHER
} tree_side;

static void
print_x(int level)
{
  char buffer[level+16];
  char* p = &buffer[0];
  append(p, "+ ");
  for (int i = 0; i < level; i++)
    append(p, " ");
  append(p, "X");
  printf("%s\n", buffer);
}

#endif
