
#ifndef PROFILE_H
#define PROFILE_H

#ifndef ENABLE_PROFILE
#define ENABLE_PROFILE 1
#endif

#if ENABLE_PROFILE == 1

void profile_init(int size);
void profile_entry(double timestamp, const char* message);
void profile_write(int rank, FILE* file);
void profile_finalize(void);

#else

#define profile_init(s)     ;
#define profile_entry(m,t)  ;
#define profile_write(r,f)  ;
#define profile_finalize()  ;

#endif

#endif
