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

package exm.stc.tclbackend.tree;

import java.util.List;

/**
 * Tcl list, e.g., [ list ... ]
 * @author wozniak
 * */
public class TclList extends Square
{
  public TclList()
  {
    super();

    add(new Token("list"));
    setTreatAsList(true);
  }

  public TclList(Expression... listItems) {
    this();
    addAll(listItems);
  }

  public TclList(List<? extends Expression> listItems)
  {
    this();
    addAll(listItems);
  }

  public TclList(String... strings)
  {
    this();
    for (String s : strings)
      add(new Token(s));
  }

  @Override
  public boolean supportsStringList() {
    // Won't be correctly quoted
    // TODO: can just add braces
    return false;
  }
}
