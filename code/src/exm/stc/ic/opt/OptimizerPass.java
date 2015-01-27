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
package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.UserException;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * An optimizer pass
 */
public interface OptimizerPass {
  public abstract String getPassName();
  /**
   * @return Key indicating whether pass is enabled.  If null, always enabled
   */
  public abstract String getConfigEnabledKey();
  public abstract void optimize(Logger logger, Program program)
                                              throws UserException;
  
  public static abstract class FunctionOptimizerPass implements OptimizerPass {

    @Override
    public void optimize(Logger logger, Program program) throws UserException {
      for (Function f: program.functions()) {
        optimize(logger, f);
      }
    }
    
    public abstract void optimize(Logger logger, Function f) throws UserException;
  }
}
