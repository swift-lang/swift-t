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

public class LiteralInt extends Expression {

  public static final Expression TRUE = new LiteralInt(1);
  public static final Expression FALSE = new LiteralInt(0);

  public static final Expression ZERO = new LiteralInt(0);
  public static final Expression ONE = new LiteralInt(1);
  public static final Expression TWO = new LiteralInt(2);

  private final long value;

  public LiteralInt(long value) {
    this.value = value;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    sb.append(Long.toString(value));
  }

  public static Expression boolValue(boolean val) {
    return val ? TRUE : FALSE;
  }

  public long value() {
    return value;
  }

  public boolean boolValue() {
    return value != 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof LiteralInt))
      return false;
    return ((LiteralInt)o).value == this.value;
  }

  @Override
  public boolean supportsStringList() {
    return true;
  }
}
