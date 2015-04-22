import assert;

type superint int;

superint y;

y = superint(2);

trace(y);
assertEqual(y, 2, "y");
