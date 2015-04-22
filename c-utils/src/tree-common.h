/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

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
  for (int i = 0; i < level; i++)
    append(p, " ");
  append(p, "X");
  printf("%s\n", buffer);
}

#endif
