#ifndef __BENCH_UTIL
#define __BENCH_UTIL

#include <sys/time.h>
#include <math.h>

// Random utils
static double rand_float(void);
static inline double lognorm_sample(double mu, double sigma);
static inline double norm_sample(double mu, double sigma);

// Wait without syscall
static inline void spin(double sleep_time);

// Float in (0, 1) range
static double rand_float(void)
{
  double v = (double)rand();

  // scale, non-inclusive  

  return (v + 1) / ((double)RAND_MAX + 2);
}

static double norm_sample(double mu, double sigma)
{
  while (true)
  {
    double r1 = rand_float() * 2 - 1;
    double r2 = rand_float() * 2 - 1;
    double s = r1*r1 + r2*r2;
    if (s < 1)
    {
      double unscaled = r1 * sqrt((-2 * log(s))/s);
      return mu + sigma * unscaled;
    }
  }
}

static double lognorm_sample(double mu, double sigma)
{
  return exp(norm_sample(mu, sigma));
}

static inline void spin(double sleep_time)
{
  // Use this time API - same as Tcl
  struct timeval spin_start, now;
  gettimeofday(&spin_start, NULL);
  long sleep_time_usec = (long)(sleep_time * 1e6);
  
  now = spin_start;
  while (now.tv_sec * 1000000 + now.tv_usec >
         spin_start.tv_sec * 1000000 + spin_start.tv_usec +
         sleep_time_usec) {
    gettimeofday(&now, NULL);
  }
}
#endif // __BENCH_UTIL
