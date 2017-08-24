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

// UNIX.SWIFT

#ifndef UNIX_SWIFT
#define UNIX_SWIFT

app (file o) cp(file i)
{
  "cp" i o;
}

// cat-print (to stdout)
app catp(file f[])
{
  "cat" f;
}

app (file o) cat(file f[])
{
  "cat" f @stdout=o;
}

app (file o) sed(file i, string command)
{
  "sed" command i @stdout=o;
}

app (file o) touch()
{
  "touch" o;
}

app printenv()
{
  "printenv";
}

app (file o) echo(string s)
{
  "echo" s @stdout=o;
}

app (void v) sleep(int i)
{
  "sleep" i;
}

app (void v) mkdir(string dirname)
{
  "mkdir" "-p" dirname;
}

app (void o) rm(string flags, string dirname)
{
  "rm" flags dirname;
}

#endif
