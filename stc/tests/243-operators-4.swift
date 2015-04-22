
import assert;

main {
    assertEqual(7 %% 3, 1, "7 %% 3 = 1");
    assertEqual(8 %% 3, 2, "8 %% 3 = 2");
    assertEqual(7 %% -3, 1, "7 %% -3 = 1");
    assertEqual(8 %% -3, 2, "8 %% -3 = 2");

    assertEqual(-7 %% 3, -1, "-7 %% 3 = -1");
    assertEqual(-8 %% 3, -2, "-8 %% 3 = -2");

    assertEqual(-7 %% -3, -1, "-7 %% -3 = -1");
    assertEqual(-8 %% -3, -2, "-8 %% -3 = -2");

    assertEqual(5 %/ 3, 1, "5 %/ 3 = 1");
    assertEqual(7 %/ 3, 2, "7 %/ 3 = 2");
    assertEqual(1 %/ 3, 0, "1 %/ 3 = 0");
    assertEqual(-7 %/ 3, -2, "-7 %/ 3 = -2");
    assertEqual(7 %/ -3, -2, "7 %/ -3 = -2");
    assertEqual(-7 %/ -3, 2, "-7 %/ -3 = 2");
}
