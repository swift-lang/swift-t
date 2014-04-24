
program prog

use f

implicit none

double precision :: x

integer :: argc
type (string_array) argv

integer i
character (len=1024) tmp

print *, 'starting prog...'

! SNIPPET 1
argc = command_argument_count()
call string_array_create(argv, argc)

do i = 1, argc
   call get_command_argument(i, tmp)
   call string_array_set(argv, i, tmp)
end do
! SNIPPET END

call func(argc, argv, x)

if (argc .ne. 0) then
   print *, 'writing to: ', trim(tmp)
   open (unit=1, file=tmp)
   write (1,*) 'x = ', x
   close (1)
endif

print *, 'prog done.'
end program prog
