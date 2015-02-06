- inlets for which `init = true` should only  be plugged to constants
- inlets for which `rate = ugen` should never be plugged to constants
- should handle variadic inputs

- add variants of Line, ExpLine etc. that drop the doneAction argument
- insert-mutation (where a UGen is inserted in an existing edge)
- cross-over could with a certain probability also create children
  that are larger than the parents, in order to be able to better
  combine complementary sounds
- mutationIter should be a range (min/max), in order to occasionally
  allow more radical changes?
  
