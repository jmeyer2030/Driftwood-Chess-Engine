## Estimating Blitz Elo:

### Setup & Results:

#### Hardware:
CCRL Blitz operates in 2:00+1, which on my hardware is roughly 1:00+0.5

#### Engines Tested: https://computerchess.org.uk/404/rating_list_all.html
```
Number Name              Strength Link
1      Rengar 2.0.0      2849     https://github.com/teswayze/rengar
2      Seawall 20250322  2975     https://github.com/petur/seawall/releases/tag/r20250322
3      Monty v0.1.0      3046     https://github.com/jw1912/monty/releases/tag/v0.1.0
4      Cheers v1.0.0     3033     https://github.com/Algorhythm-sxv/Cheers/releases/tag/v1.0.0
5      Kobra 2.0         3198     https://github.com/jasper-sinclair/kobra/releases/tag/2.0
6      Clarity V3.0.0    3133     https://github.com/Vast342/Clarity/releases/tag/V3.0.0
7      MIDA 2.3: https   3109     https://github.com/GiacomoPorpiglia/Mida/releases/tag/v2.3
8      Winter 1.0        3176     https://github.com/rosenthj/Winter/releases/tag/v1.0
```

```
Test Result:
--------------------------------------------------
Rank Name                             Elo        +/-       nElo        +/-      Games      Score       Draw           Ptnml(0-2)
1 Kobra                         197.34      56.12     249.16      56.75        144      75.7%      12.5%    [0, 9, 9, 25, 29]
2 Winter                        194.08      53.61     254.47      56.75        144      75.3%      22.2%   [1, 4, 16, 23, 28]
3 Clarity                       134.17      41.20     205.08      56.75        144      68.4%      30.6%   [0, 6, 22, 29, 15]
4 Cheers                        128.62      52.08     154.90      56.75        144      67.7%      27.8%   [3, 7, 20, 20, 22]
5 MIDA                          128.62      52.08     154.90      56.75        144      67.7%      31.9%   [3, 6, 23, 17, 23]
6 Seawall                        75.98      44.53     100.57      56.75        144      60.8%      33.3%  [1, 14, 24, 19, 14]
7 Monty                          -2.41       4.70     -29.16      56.75        144      49.7%      98.6%     [0, 1, 71, 0, 0]
8 Rengar                        -73.46      47.09     -91.81      56.75        144      39.6%      37.5%   [15, 17, 27, 9, 4]
9 Driftwood                     -93.26      16.56    -118.58      20.06       1152      36.9%      36.8% [135, 142, 212, 64, 23]
--------------------------------------------------
```
Driftwood scored 425/1152 points

Elo Estimation:

For a given Driftwood rating, the expected score would be the sum of the individual expected match results, based on the number of games and the opponents elo:
```
def expected_total(driftwood_rating):
    total = 0.0
    for opponent_rating in ratings.values():
        p = 1 / (1 + 10 ** ((opponent_rating - driftwood_rating) / 400))
        total += games_per_opponent * p
    return total
```

We can try different driftwood elos within a range, to determine the value that best fits.
```
lo, hi = 2500, 3300

for _ in range(100):
    mid = (lo + hi) / 2
    if expected_total(mid) < actual_points:
        lo = mid
    else:
        hi = mid

rating = (lo + hi) / 2
print(rating)
print(expected_total(rating))
```

We get 2965.04 Elo with 425.00 expected total score