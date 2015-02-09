package exm.stc.frontend.typecheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SubType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.util.Pair;
import exm.stc.frontend.GlobalContext;
import exm.stc.frontend.typecheck.FunctionTypeChecker.FnCallInfo;

public class FunctionTypeCheckerTest {

  private static final Type INT_OR_FLOAT = UnionType.createUnionType(Types.F_INT, Types.F_FLOAT);
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
        FunctionTypeChecker.selectArgType(Types.F_FLOAT, Types.F_FLOAT, false));
  }

  @Test
  public void testSelectArgTypeSubtype() {
    SubType float2 = new SubType(Types.F_FLOAT, "float2");
    assertEquals("Subtype upcasting should work",
        Pair.create(Types.F_FLOAT, float2),
        FunctionTypeChecker.selectArgType(Types.F_FLOAT, float2, false));

    assertTrue("Subtype downcasting not allowed",
        FunctionTypeChecker.selectArgType(float2, Types.F_FLOAT, false) == null);
  }

  @Test
  public void testSelectArgTypeUnion() {
    assertEquals("Int matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        FunctionTypeChecker.selectArgType(Types.F_INT, INT_OR_FLOAT, false));

    assertEquals("Float matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        FunctionTypeChecker.selectArgType(Types.F_INT, INT_OR_FLOAT, false));

    assertEquals("IntOrFloat matches IntOrFloat",
        Pair.create(Types.F_INT, Types.F_INT),
        FunctionTypeChecker.selectArgType(INT_OR_FLOAT, INT_OR_FLOAT, false));

    assertEquals("IntOrFloat matches Float",
        Pair.create(Types.F_FLOAT, Types.F_FLOAT),
        FunctionTypeChecker.selectArgType(INT_OR_FLOAT, Types.F_FLOAT, false));
  }

  private static final FunctionType VARARGS_TYPE = new FunctionType(
                            Arrays.asList(Types.F_INT, INT_OR_FLOAT),
                            Arrays.asList(Types.F_STRING), true);

  @Test
  public void matchNoVarArgs() throws TypeMismatchException {
    List<FunctionType> concreteTypes;
    FnCallInfo fc = new FnCallInfo("varargsFunction", VARARGS_TYPE,
        Arrays.asList(Types.F_INT, Types.F_FLOAT, Types.F_INT, Types.F_FLOAT));
    concreteTypes = FunctionTypeChecker.concretiseFunctionCall(FAKE_CONTEXT, fc);

    assertEquals("One matching type", 1, concreteTypes.size());
    assertEquals("Varargs expanded", Arrays.asList(Types.F_INT, Types.F_FLOAT,
                      Types.F_INT, Types.F_FLOAT), concreteTypes.get(0).getInputs());
  }

  @Test
  public void matchMultiVarArgs() throws TypeMismatchException {
    FnCallInfo fc = new FnCallInfo("varargsFunction", VARARGS_TYPE,
                                    Arrays.asList(Types.F_INT));
    List<FunctionType> concreteTypes;
    concreteTypes = FunctionTypeChecker.concretiseFunctionCall(FAKE_CONTEXT, fc);

    assertEquals("One matching type", 1, concreteTypes.size());
    assertEquals("Varargs expanded", Arrays.asList(Types.F_INT),
                 concreteTypes.get(0).getInputs());
  }

  @Test
  public void matchVarArgsFail() throws TypeMismatchException {
    exception.expect(TypeMismatchException.class);
    FnCallInfo fc = new FnCallInfo("varargsFunction", VARARGS_TYPE,
                      Arrays.asList(Types.F_INT, Types.F_STRING));
    FunctionTypeChecker.concretiseFunctionCall(FAKE_CONTEXT, fc);
  }

}
