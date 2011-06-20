
#ifndef TCL_TURBINE_H
#define TCL_TURBINE_H

/**
   objc should be equal to count.  If not, fail.
*/
#define TCL_ARGS(count) { if (objc != count) { \
    char* tmp = Tcl_GetStringFromObj(objv[0], NULL);            \
    printf("command %s requires %i arguments, received %i\n",  \
           tmp, count, objc);                                   \
    return TCL_ERROR;                                           \
    }                                                           \
  }

#endif
