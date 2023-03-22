# sentymenty

sentiment analysis on contrast comments on two youtube videos -
one containing so-called "freak fight" (poor mans box)
and second with "good vibes" music

## resources

### negative(?) sentiment video
https://www.youtube.com/watch?v=plNJOq9pMnY&ab_channel=FAMEMMA
### positive(?) sentiment video

... TBC

## usage

in order to run it locally you need to provide environmental variable called
`YOUTUBE_API_KEY` and set it accordingly

## things to consider

- you should value the comments by the number of reactions to it - relevance
- use conn manager https://cljdoc.org/d/clj-http/clj-http/3.12.3/api/clj-http.conn-mgr#make-reusable-conn-manager
- cache results - save youtube api quota
