# Order Book

A simplified order book implementation written in Java.
This project is for personal exploration and learnings.

Run `mvn verify` for a benchmark. The following results were measured on an
Apple M1 with Oracle JDK 21.

```
8,4 M random order placements in 33,6 M packets took 668 ns on average
16,6 M simulated order placements and 0,0 M order cancelations took 185 ns on average with 262144 open orders
16,3 M simulated order placements and 0,0 M order cancelations took 186 ns on average with 1048576 open orders
```
