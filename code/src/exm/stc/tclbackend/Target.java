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
package exm.stc.tclbackend;

import exm.stc.common.lang.Arg;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.LiteralInt;
import exm.stc.tclbackend.tree.Value;

/**
 * The target of a Turbine rule 
 * Becomes the target of the ADLB task
 * The target is either ADLB_RANK_ANY or an non-negative integer rank
 * @author wozniak
 * */
public class Target {
  public static final Target RANK_ANY = new Target(true, new LiteralInt(-1));
  
  public final boolean rankAny;
  public final Expression targetRank;
  
  public static final Value ADLB_RANK_ANY = new Value("adlb::RANK_ANY");
  
  public Target(boolean rankAny, Expression targetRank) {
    this.rankAny = rankAny;
    this.targetRank = targetRank;
  }
  
  /**
     Constructor: Targets task to specific target rank
   */
  public static Target rank(Expression targetRank) {
    return new Target(false, targetRank);
  }
  
  public Expression toTcl() {
    if (rankAny) {
      return ADLB_RANK_ANY;
    } else {
      return targetRank;
    }
  }

  public static Target fromArg(Arg arg) {
    if (arg == null) {
      return RANK_ANY;
    } else {
      return Target.rank(TclUtil.argToExpr(arg));
    }
  }
}
