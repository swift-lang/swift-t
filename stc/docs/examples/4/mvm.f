
! Matrix-Vector Multiply: y = A*x via BLAS
      subroutine MVM(A, x, y, n)

      integer, intent(IN) :: n
      double precision, intent(IN)  :: A(n,n)
      double precision, intent(IN)  :: x(n)
      double precision, intent(OUT) :: y(n)

      double precision :: alpha, beta

      alpha = 1.0D0
      beta  = 0.0D0
      call dgemv('N', n, n, alpha, A, n, x, 1, beta, y, 1)

      end subroutine
