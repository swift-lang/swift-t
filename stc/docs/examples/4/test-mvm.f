
      program testMVM

      parameter (n = 2)
      double precision :: A(n,n)
      double precision :: x(n)
      double precision :: y(n)
      double precision :: alpha, beta

      open (unit=1, file='A.data', form='unformatted',
     $      access='direct', recl=n*n*8)
      read (1, rec=1) A
      close (1)

      open (unit=1, file='x.data', form='unformatted',
     $      access='direct', recl=n*8)
      read (1, rec=1) x
      close (1)

      do i = 1,n
         y(i) = 0.0D0
      end do

      call MVM(A, x, y, n)

      print *, "y"
      print *, y(1)
      print *, y(2)

      end program
