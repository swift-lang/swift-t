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
package exm.stc.tclbackend;

import exm.stc.tclbackend.tree.Token;

/**
 * The target of a Turbine rule 
 * Becomes the target of the ADLB task
 * The target is either ADLB_RANK_ANY or an non-negative integer rank
 * @author wozniak
 * */
public class Target
{
  boolean rankAny = false;
  int targetRank = -1;
  
  static final Token ADLB_RANK_ANY = new Token("$adlb::RANK_ANY");
  
  Target(boolean rankAny, int targetRank)
  {
    this.rankAny = rankAny;
    this.targetRank = targetRank;
  }
  
  /**
     Constructor: Targets task to any rank
   */
  static Target rankAny()
  {
    return new Target(true, -1);
  }
  
  /**
     Constructor: Targets task to specific target rank
   */
  static Target rank(int targetRank)
  {
    return new Target(false, targetRank);
  }
  
  Token toTcl()
  {
    if (rankAny)
      return ADLB_RANK_ANY;
    else
      return new Token(targetRank);
  }
}
