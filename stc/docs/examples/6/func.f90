
module f

 type string_array
    integer :: count
    character(len=1024), allocatable :: s(:)
 end type

contains

  subroutine string_array_create(array, count)
    type (string_array) :: array
    integer, intent(in) :: count
    allocate( character(len=1024) :: array%s(count) )
    array%count = count
  end subroutine

 subroutine string_array_set(array, i, v)
    type (string_array) :: array
    integer, intent(in) :: i
    character(len=1024), intent(in) :: v
    array%s(i) = v
  end subroutine

  ! Calling this function is the point of this whole example!
  ! SNIPPET 1
  subroutine func(argc, argv, output)

    implicit none

    integer, intent(in) :: argc
    type (string_array) :: argv
    double precision, intent(out) :: output
    ! SNIPPET END

    integer i

    print *, 'in func()...'

    print *, 'argc: ', argc
    do i = 1,argc
       print *, 'argv: ', trim(argv%s(i))
    end do

    output = 3.14

    print *, 'func() done.'

  end subroutine

end module
