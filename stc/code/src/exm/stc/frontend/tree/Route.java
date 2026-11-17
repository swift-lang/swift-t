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

import static exm.stc.frontend.tree.AstBuild.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.Token;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.frontend.Context;

/**
 * The route statement:
 * <pre>
 *   route (string t, A, string B[string]) {
 *     case "k1":       ... // runs when A["k1"] is inserted
 *     pattern "[kj]+": ... // regexp; runs for each match when A closes
 *     match "k*":      ... // glob; runs for each match when A closes
 *     default:         ... // keys no other branch took, when A closes
 *   }
 * </pre>
 * t is bound to the matched key.  Every matching branch runs.  B is
 * optional, declared in the enclosing scope.  B may instead name an
 * existing variable, as in route (string t, A, B): that generates no
 * code, but some branch must use B.
 *
 * Before analysis, each route is rewritten into ordinary statements:
 * <pre>
 *   string B[string];
 *   if (route_arrives(A, "k1")) { string t = "k1"; ... }
 *   wait (A) {
 *     foreach k in keys_string(A) {
 *       boolean p = regexp_test("[kj]+", k);
 *       boolean g = glob_test("k*", k);
 *       if (p) { string t = k; ... }
 *       if (g) { string t = k; ... }
 *       if (!(k == "k1" || p || g)) { string t = k; ... }
 *     }
 *   }
 * </pre>
 */
public class Route {

  public static final String CONSTRUCT = "route";

  private static final String ARRIVES = "route_arrives";
  private static final String REGEXP = "regexp_test";
  private static final String GLOB = "glob_test";
  private static final String KEYS = "keys_string";

  /** Generated names contain ':' so they cannot clash with user names */
  private static final String HIDDEN_PREFIX = "__route:";

  private final Context context;
  private final String moduleName;
  private final LineMapping lineMapping;
  /** Makes generated names unique within a module */
  private int counter = 0;

  private Route(Context context, String moduleName,
                LineMapping lineMapping) {
    this.context = context;
    this.moduleName = moduleName;
    this.lineMapping = lineMapping;
  }

  /**
   * Rewrite every route statement in the module.
   */
  public static void expand(Context context, SwiftAST programTree,
      String moduleName, LineMapping lineMapping) throws UserException {
    assert(programTree.getType() == ExMParser.PROGRAM);
    new Route(context, moduleName, lineMapping).walk(programTree);
  }

  /** Post-order, so that nested routes are expanded first */
  private void walk(SwiftAST tree) throws UserException {
    // Back to front, so an expansion does not shift indices still to do
    for (int i = tree.getChildCount() - 1; i >= 0; i--) {
      SwiftAST child = tree.child(i);
      walk(child);
      if (child.getType() == ExMParser.ROUTE_STATEMENT) {
        SwiftAST expansion = expandRoute(child);
        int parentType = tree.getType();
        if (parentType != ExMParser.PROGRAM &&
            parentType != ExMParser.BLOCK) {
          // E.g. the second statement of a chain: keep it one statement
          SwiftAST block = node(child.getToken(), ExMParser.BLOCK, "BLOCK");
          block.addChildren(expansion.children());
          expansion = block;
        }
        tree.replaceChildren(i, i, expansion);
      }
    }
  }

  private static class Branch {
    final SwiftAST tree;
    final String label; // null for default
    final SwiftAST body;
    Branch(SwiftAST tree, String label, SwiftAST body) {
      this.tree = tree;
      this.label = label;
      this.body = body;
    }
  }

  /**
   * @return nil-rooted list of statements to splice in
   */
  private SwiftAST expandRoute(SwiftAST route) throws UserException {
    sync(route);
    Token pos = route.getToken();

    SwiftAST keyTree = route.child(0);
    String keyType = keyTree.child(0).getText();
    String t = keyTree.child(1).getText();
    String a = route.child(1).getText();
    SwiftAST output = route.child(2);
    SwiftAST outputDecl = null;
    String existingOutput = null;
    if (output.getChildCount() > 0) {
      if (output.child(0).getType() == ExMParser.DECLARATION) {
        outputDecl = output.child(0);
      } else {
        existingOutput = output.child(0).getText();
      }
    }

    if (!keyType.equals("string")) {
      throw new UserException(context, CONSTRUCT + ": key variable '" + t +
          "' must be declared string, not " + keyType);
    }
    if (t.equals(a)) {
      throw new UserException(context, CONSTRUCT + ": key variable '" + t +
          "' must not have the same name as the input array");
    }
    if (outputDecl != null) {
      checkOutput(outputDecl, t, a);
    }
    if (existingOutput != null &&
        (existingOutput.equals(t) || existingOutput.equals(a))) {
      throw new UserException(context, CONSTRUCT + ": output variable '" +
          existingOutput +
          "' must not have the same name as the key or input array");
    }

    List<Branch> cases = new ArrayList<Branch>();
    List<Branch> patterns = new ArrayList<Branch>();
    List<Branch> matches = new ArrayList<Branch>();
    Branch dflt = null;
    Set<String> caseLabels = new HashSet<String>();

    for (SwiftAST branch: route.children(3)) {
      sync(branch);
      int type = branch.getType();
      if (type == ExMParser.ROUTE_DEFAULT) {
        if (dflt != null) {
          throw new UserException(context, CONSTRUCT +
              ": only one default branch is allowed");
        }
        dflt = new Branch(branch, null, branch.child(0));
      } else {
        String label = Literals.extractLiteralString(context,
                                                     branch.child(0));
        Branch b = new Branch(branch, label, branch.child(1));
        if (type == ExMParser.ROUTE_CASE) {
          if (!caseLabels.add(label)) {
            throw new UserException(context, CONSTRUCT +
                ": duplicate case \"" + label + "\"");
          }
          cases.add(b);
        } else if (type == ExMParser.ROUTE_PATTERN) {
          patterns.add(b);
        } else if (type == ExMParser.ROUTE_MATCH) {
          matches.add(b);
        } else {
          throw new STCRuntimeError("Unexpected route branch: " +
                                    branch.getText());
        }
      }
    }

    List<Branch> all = new ArrayList<Branch>();
    all.addAll(cases);
    all.addAll(patterns);
    all.addAll(matches);
    if (dflt != null) {
      all.add(dflt);
    }
    for (Branch b: all) {
      checkNoWrite(b.body, a);
    }
    if (existingOutput != null) {
      // Naming an existing output generates no code, so require a use:
      // otherwise a misspelled name would go unnoticed
      boolean used = false;
      for (Branch b: all) {
        used = used || mentions(b.body, existingOutput);
      }
      if (!used) {
        throw new UserException(context, CONSTRUCT + ": output variable '" +
            existingOutput + "' is not used in any branch");
      }
    }

    SwiftAST result = nil();
    if (outputDecl != null) {
      result.addChild(outputDecl);
    }

    // case branches: each waits for its own key
    for (Branch b: cases) {
      Token bpos = b.tree.getToken();
      SwiftAST cond = call(bpos, ARRIVES, variable(bpos, a),
                           stringLit(bpos, b.label));
      SwiftAST body = bindKey(bpos, t, stringLit(bpos, b.label), b.body);
      result.addChild(ifStmt(bpos, cond, body));
    }

    // pattern, match, default branches: when A is closed, check each key
    if (!patterns.isEmpty() || !matches.isEmpty() || dflt != null) {
      result.addChild(keyLoop(pos, t, a, cases, patterns, matches, dflt));
    }

    if (result.getChildCount() == 0) {
      // Nothing to do, but replaceChildren() needs something to splice
      result.addChild(node(pos, ExMParser.BLOCK, "BLOCK"));
    }
    return result;
  }

  /**
   * wait (A) { foreach k in keys_string(A) { ... } }
   */
  private SwiftAST keyLoop(Token pos, String t, String a,
      List<Branch> cases, List<Branch> patterns, List<Branch> matches,
      Branch dflt) {
    int id = counter++;
    String k = HIDDEN_PREFIX + id + ":key";
    SwiftAST loopBody = node(pos, ExMParser.BLOCK, "BLOCK");

    // Evaluate each pattern once per key; default reuses the results
    List<SwiftAST> taken = new ArrayList<SwiftAST>();
    for (Branch c: cases) {
      taken.add(node(pos, ExMParser.OPERATOR, "OPERATOR",
                     node(pos, ExMParser.EQUALS, "=="),
                     variable(pos, k), stringLit(pos, c.label)));
    }
    List<SwiftAST> ifs = new ArrayList<SwiftAST>();
    addPatternBranches(loopBody, ifs, taken, patterns, REGEXP, "pattern",
                       id, t, k);
    addPatternBranches(loopBody, ifs, taken, matches, GLOB, "match",
                       id, t, k);
    for (SwiftAST s: ifs) {
      loopBody.addChild(s);
    }

    if (dflt != null) {
      Token dpos = dflt.tree.getToken();
      SwiftAST body = bindKey(dpos, t, variable(dpos, k), dflt.body);
      if (taken.isEmpty()) {
        loopBody.addChild(body);
      } else {
        SwiftAST any = taken.get(0);
        for (int i = 1; i < taken.size(); i++) {
          any = node(dpos, ExMParser.OPERATOR, "OPERATOR",
                     node(dpos, ExMParser.OR, "||"), any, taken.get(i));
        }
        SwiftAST none = node(dpos, ExMParser.OPERATOR, "OPERATOR",
                             node(dpos, ExMParser.NOT, "!"), any);
        loopBody.addChild(ifStmt(dpos, none, body));
      }
    }

    SwiftAST keys = call(pos, KEYS, variable(pos, a));
    SwiftAST foreach = node(pos, ExMParser.FOREACH_LOOP, "FOREACH_LOOP",
                            keys, loopBody, node(pos, ExMParser.ID, k));
    SwiftAST waitArgs = node(pos, ExMParser.ARGUMENT_LIST, "ARGUMENT_LIST",
                             variable(pos, a));
    SwiftAST waitBody = node(pos, ExMParser.BLOCK, "BLOCK", foreach);
    return node(pos, ExMParser.WAIT_STATEMENT, "WAIT_STATEMENT",
                waitArgs, waitBody);
  }

  /**
   * For each branch: declare a boolean holding the test result in
   * decls, add the guarded body to ifs, and add the boolean to taken.
   */
  private static void addPatternBranches(SwiftAST decls, List<SwiftAST> ifs,
      List<SwiftAST> taken, List<Branch> branches, String testFunction,
      String kind, int id, String t, String k) {
    int n = 0;
    for (Branch b: branches) {
      Token bpos = b.tree.getToken();
      String flag = HIDDEN_PREFIX + id + ":" + kind + n++;
      decls.addChild(declareAssign(bpos, "boolean", flag,
          call(bpos, testFunction, stringLit(bpos, b.label),
               variable(bpos, k))));
      SwiftAST body = bindKey(bpos, t, variable(bpos, k), b.body);
      ifs.add(ifStmt(bpos, variable(bpos, flag), body));
      taken.add(variable(bpos, flag));
    }
  }

  /**
   * Prefix the branch body with string t = key, if the body uses t.
   * Skipping it otherwise avoids an unused variable warning.
   */
  private static SwiftAST bindKey(Token pos, String t, SwiftAST key,
                                  SwiftAST body) {
    if (!mentions(body, t)) {
      return body;
    }
    SwiftAST result = node(pos, ExMParser.BLOCK, "BLOCK",
                           declareAssign(pos, "string", t, key));
    result.addChildren(new ArrayList<SwiftAST>(body.children()));
    return result;
  }

  private static SwiftAST ifStmt(Token pos, SwiftAST cond, SwiftAST body) {
    return node(pos, ExMParser.IF_STATEMENT, "IF_STATEMENT", cond, body);
  }

  /** True if any identifier in tree is name */
  private static boolean mentions(SwiftAST tree, String name) {
    if (tree.getType() == ExMParser.ID && name.equals(tree.getText())) {
      return true;
    }
    for (SwiftAST child: tree.children()) {
      if (mentions(child, name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The output declaration may not be assigned or mapped in the header:
   * it would be closed before the branches could write to it.
   */
  private void checkOutput(SwiftAST decl, String t, String a)
      throws UserException {
    SwiftAST rest = decl.child(1);
    if (rest.getType() == ExMParser.DECLARE_ASSIGN) {
      throw new UserException(context, CONSTRUCT +
          ": the output variable may not be assigned in the header");
    }
    for (SwiftAST c: rest.children()) {
      if (c.getType() == ExMParser.MAPPING) {
        throw new UserException(context, CONSTRUCT +
            ": the output variable may not be mapped in the header");
      }
    }
    String b = rest.child(0).getText();
    if (b.equals(t) || b.equals(a)) {
      throw new UserException(context, CONSTRUCT + ": output variable '" +
          b + "' must not have the same name as the key or input array");
    }
  }

  /**
   * A branch may not write to the input array: a case branch for an
   * absent key is only skipped when the array is closed, so writing to
   * it from a branch could deadlock.
   */
  private void checkNoWrite(SwiftAST tree, String a) throws UserException {
    if (tree.getType() == ExMParser.ASSIGN_TARGET) {
      SwiftAST var = tree.child(0);
      if (var.getType() == ExMParser.VARIABLE &&
          var.child(0).getText().equals(a)) {
        sync(tree);
        throw new UserException(context, CONSTRUCT +
            ": a branch may not write to the input array '" + a + "'");
      }
    }
    for (SwiftAST child: tree.children()) {
      checkNoWrite(child, a);
    }
  }

  private void sync(SwiftAST tree) {
    context.syncFilePos(tree, moduleName, lineMapping);
  }
}
