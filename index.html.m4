<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <title>Swift/T - High Performance Dataflow Computing</title>
        m4_include(common.html)
    </head>

    <body id="swift-t">
        <div class="container_12">
            <!-- header -->
            <img src="banner2.gif" alt="Swift" width="750" height="89" border="0"  />
	    <!-- end header -->

			<div class="clear"></div>
            <div class="top-spacer"></div>

            <div class="grid_9">

<h1 class="pagetitle">Swift/T - High Performance Dataflow Computing</h1>

<h2 class="sc">Introduction</h2>
<p>
Swift/T is a completely new implementation of the Swift language for
high-performance computing.  In this implementation, the Swift script is
translated into an MPI program that uses the Turbine (hence, /T) and
<a href="http://www.mcs.anl.gov/project/adlb-asynchronous-dynamic-load-balancer">ADLB</a>
runtime libraries for highly scalable dataflow processing over MPI,
without single-node bottlenecks.
</p>

<center>
    <div class="button_175" title="Entry point for Swift/T language and runtime guides">
        <a href="http://swift-lang.github.io/swift-t/guide.html">User Guide</a>
    </div>
    <div class="button_175" title="Plenty of useful examples for easy parallel programming">
        <a href="http://swift-lang.github.io/swift-t/gallery.html">Gallery</a>
    </div>
    <div class="button_175" title="Swift/T installation packages and prior versions">
        <a href="http://swift-lang.github.io/swift-t/downloads.html">Downloads</a>
    </div>
</center>

<h2 class="sc">Language Overview</h2>

<p>
Swift is a naturally concurrent language with C-like syntax.  It is
primarily used to manage calls to leaf tasks- external functions
written in C, C++, Fortran, Python, R, Tcl, Julia, Qt Script, or
executable programs.  The Swift language coordinates the distribution
of data to these tasks and schedules them for concurrent execution
across the nodes of a large system.
</p>

<p>
Swift has conventional programming structures- functions, if, for,
arrays, etc.  Some functions are connected to leaf tasks, which are
expected to do the bulk of the computationally intensive work.  Tasks
run when their input data is available.  Data produced by a task may
be fed into the next using syntax like:
</p>

<div class="code">y = f(x);
z = g(y);
</div>

<p>
If tasks are able to run concurrently, they do: in this, case, two
executions of <tt>g()</tt> run concurrently.
</p>

<div class="code">y  = f(x);
z1 = g(y, 1);
z2 = g(y, 2);
</div>

<p>
Swift loops and other features provide additional concurrency
constructs. Data movement is implicitly performed over MPI.
</p>

<p>
The Swift compiler, STC, breaks a Swift script into leaf tasks and
control tasks.  These are managed at runtime by the scalable,
distributed-memory runtime consisting of Turbine and ADLB.  For
example, the following code is broken up into the task diagram:
</p>

<style>
table
{
  border: 1px solid #999;
  margin-left:  auto;
  margin-right: auto;
}
</style>

<img src="spawngraph.png" width="45%" style="float: right;  border: 1px solid #999; "/>

<div class="code" style="width:50%; z-index:-1;">int X = 100, Y = 100;
int A[][];
int B[];
foreach x in [0:X-1] {
  foreach y in [0:Y-1] {
    if (check(x, y)) {
      A[x][y] = g(f(x), f(y));
    } else {
      A[x][y] = 0;
    }
  }
  B[x] = sum(A[x]);
}
</div>


<h2 class="sc">Differences from Swift/K</h2>

<p>
The previous implementation of the Swift language is Swift/K, which
runs on the Karajan grid workflow engine (hence, /K). Karajan and its
libraries excel at wide-area computation, exploiting diverse
schedulers (PBS, Condor, etc.) and data transfer technologies.
</p>

<style>
ul
{
  list-style-type: circle;
  margin-left:50px;
}
</style>

<ul>
  <li>
    Swift/K is designed to execute a workflow of program executions
    across wide area resources.
  </li>
  <li>
    Swift/T is designed to translate a Swift script into an MPI
    program composing native code libraries for execution on a single
    large system.
  </li>
</ul>

<h2 class="sc">Migration to Swift/T</h2>

<p>
  Swift/T is able to execute Swift/K-style app functions and is a
  natural migration from Swift/K.  Key differences include:
</p>

<ul>
  <li>
    Enhanced performance: 1.5 billion tasks/s
  </li>
  <li>
    Ability to call native code functions (C, C++, Fortran)
  </li>
  <li>
    Ability to execute scripts in embedded interpreters (Python, R, Tcl, Julia, etc.)
  </li>
  <li>
    Enhanced builtin libraries (string, math, system, etc.)
  </li>
  <li>
    Runs as a single MPI job
  </li>
</ul>

<h2 class="sc">Advanced model exploration workflows</h2>

Swift/T enables high-performance model exploration workflows, such as parameter
search, optimization, and classification, to run at large scale.  See the <br/> <a href="http://emews.org">Extreme-scale Model Exploration Workflows with Swift/T (EMEWS) web site</a><br/>
for more information.

</div>

  </div>

<hr>

<p style="text-align:center;">
Last updated: m4_esyscmd(date "+%Y-%m-%d %H:%M")
</p>

</body>
</html>

<!--
 Local Variables:
 mode: html
 End:
-->
