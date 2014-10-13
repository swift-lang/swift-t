
// SKIP-THIS-TEST missing package? 

import io;
import sys;

main {
    argv_accept("board", "split1", "split2", "dfsquota");
    updateable_float solved = 0;
    printf("Opening board file %s", argv("board"));
    blob startboard = parse_board(argv("board"));

    // first generate a bunch of parallel work
    boardinfo candidates1[] = sudoku_step(solved, startboard, true,
                            toint(argv("split1", "32")));
    foreach c1 in candidates1 {
        boardinfo candidates2[] = sudoku_step(solved,
            c1.board, true, toint(argv("split2","32")));
        foreach c2 in candidates2 {
            sudoku_solve(solved, c2.board, c2.filledSquares);
        }
    }
}

sudoku_solve (updateable_float solved, blob board, int filled) {
  // Terminate if we've got a solution
  if (solved == 0.0) {
    // number of steps to take before returning
    int quota = toint(argv("dfsquota", "25000"));
    // Board size in total squares
    int boardsize = sudoku_board_size();
    boardinfo candidates3[] = @prio=filled sudoku_step(solved, board,
                                                  false, quota);
    foreach c3 in candidates3 {
      if (c3.filledSquares == boardsize) {
        solved <incr> := 1;
        print_board(c3.board);
        printf("SOLVED!");
      } else {
        @prio=c3.filledSquares sudoku_solve(solved, c3.board,
                                       c3.filledSquares);
      }
    }
  } else {
    // trace("solved elsewhere, pruning branch");
  }
}

type boardinfo {
  int filledSquares;
  blob board;
}

(blob board) parse_board (string filename) "sudoku" "0.0" [
  "set <<board>> [ sudoku::parse_board <<filename>> ] "
];

@dispatch=WORKER
(boardinfo nextboards[]) sudoku_step(updateable_float solved, blob board, 
                                        boolean breadthfirst, int quota)
                                        "sudoku" "0.0" [
    "set <<nextboards>> [ sudoku::sudoku_step <<solved>> <<board>> <<breadthfirst>> <<quota>> ]"
];

// Total squares on board
(int size) sudoku_board_size () "sudoku" "0.0" [
    "set <<size>> [ sudoku::board_size ]"
];

() print_board(blob board) "sudoku" "0.0" [
  "sudoku::print_board_tcl <<board>>"
];
