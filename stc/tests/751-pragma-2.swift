
/*
 * Test that we can define new work types and use them for foreign
 * functions.
 */

import io;

pragma worktypedef a_new_work_type;

@dispatch=a_new_work_type
(void o) f1(int i) "turbine" "0.0" [
  "puts \"f1(<<i>>) ran on $turbine::mode ([ adlb::rank ])\""
];

// Should not be case-sensitive
@dispatch=A_NEW_WORK_TYPE
(void o) f2(int i) "turbine" "0.0" [
  "puts \"f2(<<i>>) ran on $turbine::mode ([ adlb::rank ])\""
];

@dispatch=WORKER
(void o) f3(int i) "turbine" "0.0" [
  "puts \"f3(<<i>>) ran on $turbine::mode ([ adlb::rank ])\""
];

main () {

  // Try to trick into running in wrong context
  f1(1) =>
    f2(1) =>
    f3(1);

  f3(2) =>
    f1(2) =>
    f2(2);
  
  f1(3) =>
    f3(3) =>
    f2(3);
}
