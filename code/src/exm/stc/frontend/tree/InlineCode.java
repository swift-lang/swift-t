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
package exm.stc.frontend.tree;

import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.ForeignFunctions.TclOpTemplate;
import exm.stc.common.lang.ForeignFunctions.TemplateElem;
import exm.stc.frontend.Context;

public class InlineCode {
  
  /**
   * Parse simple templates where we substitute in values
   *  with names given by << >> followed by a-zA-Z0-9_{}.
   *  In the event of more than two << signs, start the substitution
   *  at the innermost pair
   * @param in
   * @return 
   */
  public static TclOpTemplate templateFromString(Context context,
                                                 String in) 
                                    throws UserException {
    TclOpTemplate template = new TclOpTemplate();
    StringBuilder currTok = new StringBuilder();
    StringBuilder currVar = null;
    boolean inVar = false;
    for (int i = 0; i < in.length(); i++) {
      char c = in.charAt(i);
      if (inVar) {
        if (Character.isLetterOrDigit(c) || c == '_') {
          currVar.append(c);
        } else if (c == '>') {
          if (i < in.length() - 1 || in.charAt(i+1) == '>') {
            i++; // Move past second >
            template.addElem(TemplateElem.createVar(currVar.toString()));
            currVar = null;
            currTok = new StringBuilder();
            inVar = false;
          } else {
            throw new InvalidSyntaxException(context, "Invalid " +
                    "variable name for substitution: did not end with " +
                    "\">>\" in template string \"" + in + "\". " + 
                    "Name up to \" " + currVar.toString() + "\" was ok");
          }
        } else {
          throw new InvalidSyntaxException(context, "Unexpected " +
                  "character '" + c + "' in variable substitution name" +
                          " after text \"" + currVar.toString() + "\" in " +
                                  "template string \"" + in + "\"");
        }
      } else {
        if (c == '<' && i < in.length() - 1 && in.charAt(i+1) == '<') {
          i++;
          while (i < in.length() - 1 && in.charAt(i+1) == '<') {
            // Find last <<, and use as part of substitution
            i++;
            currTok.append('<');
          }
          if (currTok.length() > 0) {
            template.addElem(TemplateElem.createTok(currTok.toString()));
          }
          currVar = new StringBuilder();
          currTok = null;
          inVar = true;
          
        } else if (c == '\\') {
          // Escape
          if (i < in.length() - 1) {
            i++;
            currTok.append(in.charAt(i));
          } else {
            throw new InvalidSyntaxException(context, "Trailing '\'" +
                    " in template string \"" + in + "\"");
          }
        } else {
          currTok.append(c);
        }
      }
    }
    if (inVar) {
      throw new InvalidSyntaxException(context, "Unterminated variable " +
              "substitution: unterminated variable name was " +
          "\"" + currVar.toString() + "\" in " +
          "template string \"" + in + "\"");
    } else {
      if (currTok.length() > 0) {
        template.addElem(TemplateElem.createTok(currTok.toString()));
      }
    }
    return template;
  }
}
