import location;
import assert;

@dispatch=CONTROL
(int rank) f(int i) "turbine" "0.0.1" [
  "set <<rank>> [ adlb::rank ] ; puts \"HELLO <<i>>\""
];


@dispatch=WORKER
(int rank) g(int i) "turbine" "0.0.1" [
  "set <<rank>> [ adlb::rank ] ; puts \"HELLO <<i>>\""
];

main {
  foreach i in [1:50] {
    int engine_rank = random_engine().rank;
    assertEqual(@location=location_from_rank(engine_rank)f(0),
                engine_rank, "f(0)"); 

    f(1);
    
    int worker_rank = randomWorkerRank();
    assertEqual(@location=location_from_rank(worker_rank)g(2),
                worker_rank, "g(2)");

    g(3);
  }
}
