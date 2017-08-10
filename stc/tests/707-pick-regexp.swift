
import string;

L = [ "abc", "k32", "kb2", "bac" ];
S = pick_regexp(".b.*", L);
trace(join(S, " "));
