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
 * Copyright [yyyy] [name of copyright owner]
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
 * limitations under the License..
 */

package exm.stc.tclbackend.tree;

import java.util.*;

public class Command extends TclTree
{
  List<TclTree> tokens;

  public Command(TclTree... tokens)
  {
    this.tokens = Arrays.asList(tokens);
  }

  public Command(String cmd, List<? extends Expression> args) {
    tokens = new ArrayList<TclTree>(args.size() + 1);
    tokens.add(new Token(cmd));
    tokens.addAll(args);
  }
  
  public Command(String... strings)
  {
    tokens = new ArrayList<TclTree>(strings.length);
    for (String s : strings)
      tokens.add(new Token(s));
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    Iterator<TclTree> it = tokens.iterator();
    while (it.hasNext())
    {
      TclTree tree = it.next();
      tree.appendTo(sb);
      if (it.hasNext())
        sb.append(' ');
    }
    sb.append('\n');
  }
}
