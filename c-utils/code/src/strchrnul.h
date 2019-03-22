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
 * strchrnul.h
 *
 *  Created on: Mar 21, 2019
 *      Author: wozniak
 *
 * For systems that do not have strchrnul() -- BSD, Mac
 */

#pragma once 

#ifndef HAVE_STRCHRNUL

static inline
char* strchrnul(const char* s, int c)
{
  char* result = strchr(s, c);
  if (result == NULL)
    #pragma GCC diagnostic push
    #pragma GCC diagnostic ignored "-Wincompatible-pointer-types-discards-qualifiers"
    result = s + strlen(s);
    #pragma GCC diagnostic pop

  return result;
}

#endif
