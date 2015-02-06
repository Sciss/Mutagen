- inlets for which `init = true` should only  be plugged to constants
- inlets for which `rate = ugen` should never be plugged to constants
- should handle variadic inputs

- insert-mutation (where a UGen is inserted in an existing edge)
- mutationIter should be a range (min/max), in order to occasionally
  allow more radical changes?
- two more mutations: split and merge constant
