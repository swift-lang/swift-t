@par @dispatch=WORKER
(int status) launch(string cmd, string args[])
"launch" "0.0" "launch_tcl";

@par @dispatch=WORKER
  (int status) launch_envs(string cmd, string args[], string envs[])
"launch" "0.0" "launch_envs_tcl";

string EMPTY_SS[][];

@par @dispatch=WORKER
  (int status) launch_multi(int procs[],
                            string cmd[],
                            string argv[][],
                            string envs[][],
                            string color_setting="")
"launch" "0.0" "launch_multi_tcl";

global const int EXIT_SUCCESS  = 0;
global const int EXIT_TIMEOUT  = 124; // cf. 'man timeout'
global const int EXIT_NOTFOUND = 127; // cf. 'man system(3)'
