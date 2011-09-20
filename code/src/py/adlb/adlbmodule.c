
#include <Python.h>

#include <assert.h>
#include <stdbool.h>

#include <adlb.h>

static MPI_Comm worker_comm;
static int mpi_rank = -1;
static int adlb_rank = -1;
static int am_server;

static PyObject* PyADLB_init(PyObject *self, PyObject *args);
static PyObject* PyADLB_finalize(PyObject *self, PyObject *args);

static PyMethodDef PyADLBMethods[] = {
    {"init",  PyADLB_init, METH_VARARGS, "Calls ADLB_Init"},
    {"finalize",  PyADLB_finalize, METH_VARARGS, "Calls ADLB_Finalize"},
    {NULL, NULL, 0, NULL}
};

static struct PyModuleDef PyADLBmodule = {
    PyModuleDef_HEAD_INIT,
    "PyADLB",   /* name of module */
    NULL, /* module documentation, may be NULL */
    -1,       /* size of per-interpreter state of the module,
                or -1 if the module keeps state in global variables. */
    PyADLBMethods
};

static PyObject *PyADLBError;

PyMODINIT_FUNC
PyInit_PyADLB(void)
{
  PyObject *module = PyModule_Create(&PyADLBmodule);
  if (module == NULL)
    return NULL;

  PyADLBError = PyErr_NewException("PyADLB.error", NULL, NULL);
  Py_INCREF(PyADLBError);
  PyModule_AddObject(module, "error", PyADLBError);
  return module;
}

static PyObject *
PyADLB_init(PyObject *self, PyObject *args)
{
  // if (!PyArg_ParseTuple(args, "s", &command))
  //  return NULL;

  // int error;

  int workers;
  int servers = 1;

  int ntypes = 2;

  int argc = 0;
  char** argv = NULL;

  int type_vect[ntypes];
  for (int i = 0; i < ntypes; i++)
    type_vect[i] = i;

  int code;
  code = MPI_Init(&argc, &argv);
  assert(code == MPI_SUCCESS);

  int mpi_size;
  MPI_Comm_size(MPI_COMM_WORLD, &mpi_size);
  workers = mpi_size - servers;

  MPI_Comm_rank(MPI_COMM_WORLD, &mpi_rank);

  if (mpi_rank == 0)
  {
    // if (workers <= 0) puts("WARNING: No workers");
    // Other configuration information will go here...
  }

  int am_debug_server;
  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
  code = ADLB_Init(servers, 0, 0, ntypes, type_vect,
                   &am_server, &am_debug_server, &worker_comm);
  assert(code == ADLB_SUCCESS);

  if (! am_server)
    MPI_Comm_rank(worker_comm, &adlb_rank);

  return PyLong_FromLong(1);
}

static PyObject *
PyADLB_finalize(PyObject *self, PyObject *args)
{
  int code = ADLB_Finalize();
  assert(code == ADLB_SUCCESS);
  return PyLong_FromLong(1);
}
