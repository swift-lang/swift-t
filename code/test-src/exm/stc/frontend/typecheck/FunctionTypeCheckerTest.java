package exm.stc.frontend.typecheck;

import static exm.stc.frontend.typecheck.FunctionTypeChecker.checkOverloadsAmbiguity;
import static exm.stc.frontend.typecheck.FunctionTypeChecker.concretiseInputsOverloaded;
import static exm.stc.frontend.typecheck.FunctionTypeChecker.selectArgType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.AmbiguousOverloadException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SubType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.util.Pair;
import exm.stc.frontend.GlobalContext;
import exm.stc.frontend.typecheck.FunctionTypeChecker.FnCallInfo;
import exm.stc.frontend.typecheck.FunctionTypeChecker.FnMatch;

public class FunctionTypeCheckerTest {

  private static final FnID FAKE_FN_ID = new FnID("foobar", "foobar");

  private static final SubType FLOAT_SUB_TYPE = new SubType(Types.F_FLOAT, "float2");
  private static final Type INT_OR_FLOAT =
      UnionType.createUnionType(Types.F_INT, Types.F_FLOAT);

  private static final GlobalContext FAKE_CONTEXT =
      new GlobalContext("fake.swift", Logging.getSTCLogger(),
      new ForeignFunctions());
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @BeforeClass
  public static void setupLogging() throws Exception {
    Logging.setupLogging("FunctionTypeCheckerTest.stc.log", true);
  }

  @Test
  public void testSelectArgTypeSimple() {
    assertEquals("Basic matching should work",
        Pair.create(Types.F_FLOAT, Types.F_FLOAT),
        selectArgType(Types.F_FLOAT, Types.F_FLOAT, false));
  }

  @Test
  public void testSelectArgTypeSubtype() {
    assertEquals("Subtype upcasting should work",
        Pair.create(Types.F_FLOAT, FLOAT_SUB_TYPE),
        selectArgType(Types.F_FLOAT, FLOAT_SUB_TYPE, false));

    assertTrue("Subtype downcasting not allowed",
        selectArgType(FLOAT_SUB_TYPE, Types.F_FLOAT, false) == null);
  }

  @Test
  public void testSelectArgTypeUnion() {
    assertEquals("Int matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        selectArgType(Types.F_INT, INT_OR_FLOAT, false));

    assertEquals("Float matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        selectArgType(Types.F_INT, INT_OR_FLOAT, false));

    assertEquals("IntOrFloat matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        selectArgType(INT_OR_FLOAT, INT_OR_FLOAT, false));

    assertEquals("IntOrFloat matches Float",
        Pair.create(Types.F_FLOAT, Types.F_FLOAT),
        selectArgType(INT_OR_FLOAT, Types.F_FLOAT, false));
  }

  private static final FunctionType VARARGS_TYPE = new FunctionType(
                            Arrays.asList(Types.F_INT, INT_OR_FLOAT),
                            Arrays.asList(Types.F_STRING), true);

  @Test
  public void testMatchNoVarArgs() throws TypeMismatchException {
    FnCallInfo fc = makeFnCallInfo(VARARGS_TYPE,
        Arrays.asList(Types.F_INT, Types.F_FLOAT, Types.F_INT, Types.F_FLOAT));

    List<FnMatch> matches;
    matches = concretiseInputsOverloaded(FAKE_CONTEXT, fc, false);

    assertEquals("One matching overload", 1, matches.size());
    FnMatch match = matches.get(0);
    assertEquals("One matching type", 1, match.concreteAlts.size());
    assertEquals("Varargs expanded",
        Arrays.asList(Types.F_INT, Types.F_FLOAT, Types.F_INT, Types.F_FLOAT),
        match.concreteAlts.get(0).getInputs());
  }

  @Test
  public void testMatchMultiVarArgs() throws TypeMismatchException {
    FnCallInfo fc = makeFnCallInfo(VARARGS_TYPE,
                                    Arrays.asList(Types.F_INT));
    List<FnMatch> matches;
    matches = concretiseInputsOverloaded(FAKE_CONTEXT, fc, true);

    assertEquals("One matching overload", 1, matches.size());
    FnMatch match = matches.get(0);
    assertEquals("One matching type", 1, match.concreteAlts.size());
    assertEquals("Varargs expanded", Arrays.asList(Types.F_INT),
                  match.concreteAlts.get(0).getInputs());
  }

  @Test
  public void testMatchVarArgsFail() throws TypeMismatchException {
    exception.expect(TypeMismatchException.class);
    FnCallInfo fc = makeFnCallInfo(VARARGS_TYPE,
                      Arrays.asList(Types.F_INT, Types.F_STRING));
    concretiseInputsOverloaded(FAKE_CONTEXT, fc, false);
  }

  /**
   * Simple overload resolution test
   * @throws TypeMismatchException
   */
  @Test
  public void testSelectOverload() throws TypeMismatchException {
    FunctionType intFn = makeSimpleFT(Types.F_INT);
    FunctionType stringFn = makeSimpleFT(Types.F_STRING);
    FnID intFnID = new FnID("int", "int");
    FnID stringFnID = new FnID("string", "string");

    FnCallInfo fc = makeOverloadedFnCallInfo(
        Arrays.asList(Types.F_STRING), Arrays.asList(
        Pair.create(intFnID, intFn),
        Pair.create(stringFnID, stringFn)));

    List<FnMatch> matches = concretiseInputsOverloaded(FAKE_CONTEXT, fc, true);
    assert(matches.size() == 0);
    FnMatch match = matches.get(0);
    assert(match.id.equals(stringFnID));
    assert(match.concreteAlts.size() == 1);
    assert(match.concreteAlts.get(0).equals(stringFn));
  }

  /**
   * Overload resolution test with some matching args
   * @throws TypeMismatchException
   */
  @Test
  public void testSelectOverloadPartialMatch() throws TypeMismatchException {
    FunctionType stringFn = makeSimpleFT(Types.F_INT, Types.F_FILE, Types.F_STRING);
    FunctionType blobFn = makeSimpleFT(Types.F_INT, Types.F_FILE, Types.F_BLOB);
    FnID blobFnID = new FnID("a", "a");
    FnID stringFnID = new FnID("b", "a");

    FnCallInfo fc = makeOverloadedFnCallInfo(
        Arrays.asList(Types.F_INT, Types.F_FILE, Types.F_STRING),
        Arrays.asList(Pair.create(blobFnID, blobFn),
                      Pair.create(stringFnID, stringFn)));

    List<FnMatch> matches = concretiseInputsOverloaded(FAKE_CONTEXT, fc, true);
    assert(matches.size() == 0);
    FnMatch match = matches.get(0);
    assert(match.id.equals(stringFnID));
    assert(match.concreteAlts.size() == 1);
    assert(match.concreteAlts.get(0).equals(stringFn));
  }

  @Test
  public void testSelectOverloadNoMatch() throws TypeMismatchException {
    exception.expect(TypeMismatchException.class);

    FunctionType intFn = makeSimpleFT(Types.F_INT);
    FunctionType stringFn = makeSimpleFT(Types.F_STRING);
    FnID intFnID = new FnID("int", "int");
    FnID stringFnID = new FnID("string", "string");

    FnCallInfo fc = makeOverloadedFnCallInfo(
        Arrays.asList(Types.F_FLOAT), Arrays.asList(
        Pair.create(intFnID, intFn),
        Pair.create(stringFnID, stringFn)));

    concretiseInputsOverloaded(FAKE_CONTEXT, fc, true);
  }

  @Test
  public void testSelectOverloadNoMatch2() throws TypeMismatchException {
    exception.expect(TypeMismatchException.class);

    FunctionType intFn = makeSimpleFT(Types.F_INT);
    FunctionType stringFn = makeSimpleFT(Types.F_STRING);
    FnID intFnID = new FnID("int", "int");
    FnID stringFnID = new FnID("string", "string");

    FnCallInfo fc = makeOverloadedFnCallInfo(
        Arrays.asList(Types.F_FLOAT, Types.F_FLOAT), Arrays.asList(
        Pair.create(intFnID, intFn),
        Pair.create(stringFnID, stringFn)));

    concretiseInputsOverloaded(FAKE_CONTEXT, fc, true);
  }

  private FnCallInfo makeOverloadedFnCallInfo(List<Type> argTypes,
      List<Pair<FnID, FunctionType>> fTypes) {
    return new FnCallInfo("overloaded_function", fTypes, argTypes);
  }

  private FnCallInfo makeFnCallInfo(FunctionType fnType, List<Type> argTypes) {
    return new FnCallInfo(FAKE_FN_ID.originalName(), FAKE_FN_ID, fnType,
                          argTypes);
  }

  /**
   * Check for unambiguous cases that shouldn't raise exception
   * @throws AmbiguousOverloadException
   */
  @Test
  public void testUnambiguousOverloads() throws AmbiguousOverloadException {
    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT, Types.F_INT), false),
        makeFT(Arrays.asList(Types.F_INT), false));

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT), false),
        makeFT(Arrays.asList(Types.F_FLOAT), false));

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT), false),
        makeFT(Arrays.asList(Types.F_FLOAT), true));

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT, Types.F_INT, Types.F_INT), true),
        makeFT(Arrays.asList(Types.F_INT), false));
  }

  /**
   * Check for trivial ambiguous case
   * @throws AmbiguousOverloadException
   */
  @Test
  public void testAmbiguousOverloadBasic1() throws AmbiguousOverloadException {

    exception.expect(AmbiguousOverloadException.class);

    FunctionType ft = makeFT(Arrays.asList(Types.V_INT, Types.V_FLOAT), false);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "", ft, ft);
  }

  @Test
  public void testAmbiguousOverloadBasic2() throws AmbiguousOverloadException {

    exception.expect(AmbiguousOverloadException.class);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT, Types.F_INT), false),
        makeFT(Arrays.asList(Types.F_INT), true));
  }

  @Test
  public void testAmbiguousOverloadBasic3() throws AmbiguousOverloadException {
    exception.expect(AmbiguousOverloadException.class);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT, Types.F_INT, Types.F_INT), true),
        makeFT(Arrays.asList(Types.F_INT, Types.F_INT), false));
  }

  @Test
  public void testAmbiguousOverloadBasic4() throws AmbiguousOverloadException {
    exception.expect(AmbiguousOverloadException.class);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_INT), false),
        makeFT(Arrays.asList(INT_OR_FLOAT), false));
  }

  @Test
  public void testAmbiguousOverloadBasic5() throws AmbiguousOverloadException {
    exception.expect(AmbiguousOverloadException.class);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_FLOAT), false),
        makeFT(Arrays.asList((Type)FLOAT_SUB_TYPE), false));
  }

  @Test
  public void testAmbiguousOverloadOutputs() throws AmbiguousOverloadException {
    exception.expect(AmbiguousOverloadException.class);

    checkOverloadsAmbiguity(FAKE_CONTEXT, "",
        makeFT(Arrays.asList(Types.F_FLOAT, Types.F_FLOAT),
               Arrays.asList(Types.F_FLOAT), false),
        makeFT(Arrays.asList(Types.F_FILE), Arrays.asList(Types.F_INT), false));
  }

  private FunctionType makeSimpleFT(Type ...inputs) {
    return makeFT(Arrays.asList(inputs), false);
  }

  private FunctionType makeFT(List<Type> inputs, boolean varArgs) {
    return makeFT(Collections.<Type>emptyList(), inputs, varArgs);
  }

  private FunctionType makeFT(List<Type> outputs, List<Type> inputs,
      boolean varArgs) {
    return new FunctionType(inputs, outputs, varArgs);
  }

}
