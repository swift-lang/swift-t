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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.ic.tree.ICTree.Program;


public class OptimizerPipeline {

  public OptimizerPipeline(PrintStream icOutput) {
    this.icOutput = icOutput;
  }

  private final List<OptimizerPass> passes = new ArrayList<OptimizerPass>();
  private final PrintStream icOutput;
  private Validate validator = null;

  public void addPass(OptimizerPass pass) {
    passes.add(pass);
  }

  public void setValidator(Validate validator) {
    this.validator = validator;
  }

  public void runPipeline(Logger logger, Program program, long iteration) throws UserException {
    for (OptimizerPass pass: passes) {
      if (passEnabled(pass)) {
        logger.debug("Iteration: " + iteration + " Pass: "
                   + pass.getPassName());
        pass.optimize(logger, program);
        if (icOutput != null) {
          program.log(icOutput, "Iteration " + iteration + " IC after " +
                                 pass.getPassName());
        }
        if (validator != null) {
          validator.optimize(logger, program);
        }
      }
    }
  }

  public boolean passEnabled(OptimizerPass pass) {
    String key = pass.getConfigEnabledKey();
    return key == null || Settings.getBooleanUnchecked(key);
  }
}
