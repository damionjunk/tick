;; Copyright © 2016-2017, JUXT LTD.

(ns tick.interval
  (:refer-clojure :exclude [contains? complement partition-by group-by conj extend])
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [tick.core :as t])
  (:import
   [java.util Date]
   [java.time Instant Duration Period ZoneId LocalDate LocalTime LocalDateTime Year YearMonth OffsetDateTime ZoneId ZonedDateTime]
   [java.time.temporal Temporal TemporalAmount]))

;; Use of Allen's Interval Algebra, inspired from a working
;; demonstration of time-count by Eric Evans.

(s/def ::local (s/and #(t/local? (t/beginning %)) #(t/local? (t/end %))))
(s/def ::non-local (s/and #(not (t/local? (t/beginning %))) #(not (t/local? (t/end %)))))

(s/def ::interval
  (s/and
    (s/or :local ::local :non-local ::non-local)
    #(let [[_ t] %]
       (let [t1 (t/beginning t)
             t2 (t/end t)]
         (t/< t1 t2)))))

(defrecord Interval [beginning end]
  t/ITimeSpan
  (beginning [_] beginning)
  (end [_] end))

;; Construction

(defn- make-interval [v1 v2]
  (assert (t/< v1 v2))
  (->Interval v1 v2))

(defn temporal? [o]
  (instance? java.time.temporal.Temporal o))

(defn temporal-amount? [o]
  (instance? java.time.temporal.TemporalAmount o))

;; interval
;; [t0 t1] interval between t0 and t1
;; [t1 t0] interval between t0 and t1
;; [t0 d] interval between t0 and t0+d, where d is a given duration
;; [d t1] interval between t1-d and t1, where d is a given duration

(defn interval
  [v1 v2]
  (let [t1 (t/temporal-value v1)
        t2 (t/temporal-value v2)]
    (assert (t/< t1 t2))
    (->Interval t1 t2)))

;; Adjustments

;; (extend _ & durations) to extend the interval, add durations to the end
;; Durations can be negative, so a retraction is simply an extend with a negative duration.

(defn extend [ival dur]
  (make-interval
    (t/beginning ival)
    (t/forward-duration (t/end ival) dur)))

(defn scale [ival factor]
  (make-interval
    (t/beginning ival)
    (t/forward-duration (t/beginning ival) (.multipliedBy (t/length ival) factor))))

(extend-protocol t/ITimeShift
  Interval
  (forward-duration [ival d]
    (make-interval
      (t/forward-duration (t/beginning ival) d)
      (t/forward-duration (t/end ival) d)))
  (backward-duration [ival d]
    (make-interval
      (t/backward-duration (t/beginning ival) d)
      (t/backward-duration (t/end ival) d))))

;; An interval of duration d to t1 can be constructed like this:
;; (scale (interval t1 d) -1)


;; (>> _ d) to shift the interval into the future by duration d
;; (<< _ d) to shift the interval into the past by duration d

;; (* _) to duplicate into meeting intervals, into a sequence (possibly need a record container for this, IntervalSeq)

;; (interpose IntervalSeq d)

;; / to divide into subintervals

;; Finally, it should be possible to transduce IntervalSeqs


;; Reification

(extend-protocol t/ITimeReify
  Interval
  (on [i date] (interval (t/on (t/beginning i) date) (t/on (t/end i) date)))
  (in [i zone] (interval (t/in (t/beginning i) zone) (t/in (t/end i) zone))))

(defn bounds [& args]
  (interval
    (apply t/min (map t/beginning args))
    (apply t/max (map t/end args))))

(defn am [^LocalDate date]
  (interval (t/beginning date) (t/noon date)))

(defn pm [^LocalDate date]
  (interval (t/noon date) (t/end date)))

;; Allen's Basic Relations

(defn precedes? [x y]
  (t/< (t/end x) (t/beginning y)))

(defn equals? [x y]
  (and
    (= (t/beginning x) (t/beginning y))
    (= (t/end x) (t/end y))))

(defn meets? [x y]
  (= (t/end x) (t/beginning y)))

(defn overlaps? [x y]
  (and
   (t/< (t/beginning x) (t/beginning y))
   (t/> (t/end x) (t/beginning y))
   (t/< (t/end x) (t/end y))))

(defn during? [x y]
  (and
   (t/> (t/beginning x) (t/beginning y))
   (t/< (t/end x) (t/end y))))

(defn starts? [x y]
  (and
   (= (t/beginning x) (t/beginning y))
   (t/< (t/end x) (t/end y))))

(defn finishes? [x y]
  (and
   (t/> (t/beginning x) (t/beginning y))
   (= (t/end x) (t/end y))))

;; Six pairs of the relations are converses.  For example, the converse of "a precedes b" is "b preceded by a"; whenever the first relation is true, its converse is true also.
(defn conv
  "The converse of a basic relation."
  [f]
  (fn [x y]
    (f y x)))

(defn preceded-by? [x y] ((conv precedes?) x y))
(defn met-by? [x y] ((conv meets?) x y))
(defn overlapped-by? [x y] ((conv overlaps?) x y))
(defn finished-by? [x y] ((conv finishes?) x y))

;; contains? is semantically similar to tick.core/coincident?
(defn contains? [x y] ((conv during?) x y))
(defn started-by? [x y] ((conv starts?) x y))

(def code {precedes? \p
           meets? \m
           overlaps? \o
           finished-by? \F
           contains? \D
           starts? \s
           equals? \e
           started-by? \S
           during? \d
           finishes? \f
           overlapped-by? \O
           met-by? \M
           preceded-by? \P})

(def basic-relations
  [precedes? meets? overlaps? finished-by? contains?
   starts? equals? started-by? during? finishes? overlapped-by?
   met-by? preceded-by?])

;; Allen's General Relations

(defrecord GeneralRelation [relations]
  clojure.lang.IFn
  (invoke [_ x y]
    #_(s/assert ::interval x)
    #_(s/assert ::interval y)
    (some (fn [f] (when (f x y) f)) relations)))

;; Relations are 'basic relations' in [ALSPAUGH-2009]. Invoking a
;; general relation on two intervals returns the basic relation that
;; causes the general relation to hold. Note there can only be one
;; such basic relation due to the relations being distinct.

(defn make-relation [& basic-relations]
  (->GeneralRelation basic-relations))

(def ^{:doc "A function to determine the (basic) relation between two intervals."}
  relation
  (apply make-relation basic-relations))

;; Operations on relations

(defn complement-r
  "Return the complement of the general relation. The complement ~r of
  a relation r is the relation consisting of all basic relations not
  in r."
  [^GeneralRelation r]
  (assoc r :relations (remove (set (:relations r)) basic-relations)))

(defn compose-r
  "Return the composition of r and s"
  [r s]
  (throw (new UnsupportedOperationException "Not yet implemented")))

(defn converse-r
  "Return the converse of the given general relation. The converse !r
  of a relation r is the relation consisting of the converses of all
  basic relations in r."
  [^GeneralRelation r]
  (assoc r :relations (map conv (:relations r))))

(defn intersection-r
  "Return the intersection of the r with s"
  [^GeneralRelation r ^GeneralRelation s]
  (s/assert r #(instance? GeneralRelation %))
  (->GeneralRelation (set/intersection (set (:relations r))))
  (throw (new UnsupportedOperationException "Not yet implemented")))

;; Useful named general relations

(def disjoint? (make-relation precedes? preceded-by? meets? met-by?))
(def concur? (complement-r disjoint?))

;; Functions that make use of Allens' Interval Algebra

(defprotocol IIntervalOps
  ;; TODO: Rename? Not always narrow, sometimes widen, or generally modify - perhaps 'derive'
  (narrow [_ beginning end] "Narrow the interval to the new given bounds")
  (splice [ival1 ival2] "Splice two intervals together")
  (split [ival t] "Split ival into 2 intervals, at t"))

(defn split-with-assert [ival t]
  (assert
    (and (t/< (t/beginning ival) t)
         (t/< t (t/end ival))))
  (split ival t))

(extend-protocol IIntervalOps
  Interval
  (narrow [_ beginning end] (interval beginning end))
  (splice [ival1 ival2] (interval
                          (t/min (t/beginning ival1) (t/beginning ival2))
                          (t/max (t/end ival1) (t/end ival2))))
  (split [ival t]
    [(interval (t/beginning ival) t) (interval t (t/end ival))])

  LocalDate
  (narrow [date beginning end]
    (assert (t/<= (t/beginning date) beginning))
    (assert (t/>= (t/end date) end))
    (interval beginning end))
  (splice [ival1 ival2]
    (throw (ex-info "Not implemented" {:args [ival1 ival2]})))
  (split [ival t]
    [(interval (t/beginning ival) t) (interval t (t/end ival))])

  YearMonth
  (narrow [ym beginning end]
    (assert (t/<= (t/beginning ym) beginning))
    (assert (t/>= (t/end ym) end))
    (interval beginning end))
  (splice [ival1 ival2]
    (throw (ex-info "Not implemented" {:args [ival1 ival2]})))
  (split [ival t]
    [(interval (t/beginning ival) t) (interval t (t/end ival))])

  Year
  (narrow [yr beginning end]
    (assert (t/<= (t/beginning yr) beginning))
    (assert (t/>= (t/end yr) end))
    (interval beginning end))
  (splice [ival1 ival2]
    (throw (ex-info "Not implemented" {:args [ival1 ival2]})))
  (split [ival t]
    [(interval (t/beginning ival) t) (interval t (t/end ival))]))

(defn concur
  "Return the interval representing the interval, if there is one,
  representing the interval of time the given intervals are
  concurrent."
  [x y]
  (case (code (relation x y))
    \o (narrow x (t/beginning y) (t/end x))
    \O (narrow x (t/beginning x) (t/end y))
    (\s \f \d \e) x
    (\S \F \D) (narrow x (t/beginning y) (t/end y))
    nil))

(defn ^:experimental concurrencies
  "Return a sequence of occurances where intervals coincide (having
  non-nil concur intervals)."
  [& intervals]
  (let [intervals (vec intervals)]
    (for [xi (range (count intervals))
          yi (range (count intervals))
          :when (< xi yi)
          :let [x (get intervals xi)
                y (get intervals yi)
                conc (concur x y)]
          :when conc]
      {:x x :y y :concur concur})))

;; Comparison. We have now built up the capability of comparing
;; intervals using the normal <, >, <=, >= operators.

(defn as-interval [t]
  (when (= (t/beginning t) (t/end t))
    (throw (ex-info "t is a zero length interval!" {:t t})))
  (interval (t/beginning t) (t/end t)))

(extend-protocol t/ITimeComparison
  LocalDate
  (< [x y] (t/< (as-interval x) (as-interval y)))
  (<= [x y] (t/<= (as-interval x) (as-interval y)))
  (> [x y] (t/> (as-interval x) (as-interval y)))
  (>= [x y] (t/>= (as-interval x) (as-interval y)))
  YearMonth
  (< [x y] (t/< (as-interval x) (as-interval y)))
  (<= [x y] (t/<= (as-interval x) (as-interval y)))
  (> [x y] (t/> (as-interval x) (as-interval y)))
  (>= [x y] (t/>= (as-interval x) (as-interval y)))
  Year
  (< [x y] (t/< (as-interval x) (as-interval y)))
  (<= [x y] (t/<= (as-interval x) (as-interval y)))
  (> [x y] (t/> (as-interval x) (as-interval y)))
  (>= [x y] (t/>= (as-interval x) (as-interval y)))
  Interval
  (< [x y] (#{precedes? meets?} (relation x y)))
  (<= [x y] (#{precedes? meets? equals? starts? overlaps? finished-by?} (relation x y)))
  (> [x y] (#{preceded-by? met-by?} (relation x y)))
  (>= [x y] (#{preceded-by? met-by? equals? started-by? overlapped-by? finishes?} (relation x y))))

;; Interval sets - sequences of mutually disjoint intervals

(defn ordered-disjoint-intervals?
  "Are all the intervals in the given set temporarily ordered and
  disjoint? This is a useful property of a collection of
  intervals. The given collection must contain proper intervals (that
  is, intervals that have finite greater-than-zero durations)."
  [s]
  (let [rel (make-relation precedes? meets?)]
    (some?
     (loop [[x & xs] s]
       (if (or (nil? x) (nil? (first xs))) true
           (when (rel x (first xs))
             (recur xs)))))))

;; If we ever want to create a wrapper type of ordered disjoint
;; interval sets...
#_(deftype OrderedDisjointIntervalSet [s]
  clojure.lang.Seqable
  (seq [_] (seq s)))

(defn union
  "Combine multiple collections of intervals into a single ordered
  collection of ordered disjoint intervals."
  [& colls]
  (loop [colls colls result []]
    (let [colls (remove nil? colls)]
      (if (<= (count colls) 1)
        (clojure.core/concat result (first colls))

        (let [[c1 c2 & r] (sort-by (comp t/beginning first) colls)]
          (if (disjoint? (first c1) (first c2))
            (recur (apply list (next c1) c2 r) (clojure.core/conj result (first c1)))

            (recur (apply list
                          (next c1)
                          (clojure.core/concat [(splice (first c1) (first c2))]
                                               (next c2))
                          r)
                   result)))))))

(defn conj [coll interval]
  (union coll [interval]))

(defn intersection
  "Return an interval set that is the intersection of the input
  interval sets."
  ;; Single arity
  ([s1] s1)
  ;; 2-arity
  ([s1 s2]
   (loop [xs s1
          ys s2
          result []]
     (if (and (not-empty xs) (not-empty ys))
       (let [x (first xs)
             y (first ys)
             code (code (relation x y))]
         (case code
           (\p \m) (recur (next xs) ys result)
           (\P \M) (recur xs (next ys) result)
           \S (recur
                (cons (narrow x (t/end y) (t/end x)) (next xs))
                (next ys)
                (clojure.core/conj result (narrow x (t/beginning y) (t/end y))))
           \F (recur
                (next xs)
                (next ys)
                (clojure.core/conj result (narrow x (t/beginning y) (t/end y))))
           \o (recur
                (cons (narrow x (t/beginning y) (t/end x)) (next xs))
                (cons (narrow y (t/end x) (t/end y)) (next ys))
                (clojure.core/conj result (narrow x (t/beginning y) (t/end x))))
           \O (recur
                (cons (narrow x (t/end y) (t/end x)) (next xs))
                (next ys)
                (clojure.core/conj result (narrow x (t/beginning x) (t/end y))))
           \D (recur
                (cons (narrow x (t/end y) (t/end x)) (next xs))
                (next ys)
                (clojure.core/conj result (narrow x (t/beginning y) (t/end y))))
           \d (recur
                (next xs)
                (cons (narrow y (t/end x) (t/end y)) (next ys))
                (clojure.core/conj result x))
           \e (recur
                (next xs)
                (next ys)
                (clojure.core/conj result x))
           \f (recur
                (next xs)
                (next ys)
                (clojure.core/conj result x))
           \s (recur
                (next xs)
                (cons (narrow y (t/end x) (t/end y))
                      (next ys))
                (clojure.core/conj result x))))
       result)))
  ([s1 s2 & sets]
   (reduce intersection s1 (clojure.core/conj sets s2))))

(defn intersects? [ivals interval]
  (not-empty (intersection ivals [interval])))

(defn difference
  "Return an interval set that is the first set without elements of
  the remaining sets."
  ([s1] s1)
  ([s1 s2]
   (loop [xs s1
          ys s2
          result []]
     (if (not-empty xs)
       (if (not-empty ys)
         (let [x (first xs) y (first ys)
               code (code (relation x y))]
           (case code
             (\p \m) (recur (next xs) ys (clojure.core/conj result x))
             (\P \M) (recur xs (next ys) result)
             (\f \d \e) (recur (next xs) (next ys) result)
             \s (recur (next xs) ys result)
             (\S \O)
             (recur (cons (narrow x (t/end y) (t/end x)) (next xs)) (next ys) result)
             \F (recur (next xs) (next ys) (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))
             \o (recur (next xs) ys (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))
             \D (recur (cons (narrow x (t/end y) (t/end x)) (next xs))
                       (next ys)
                       (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))))
         (apply clojure.core/conj result xs))
       result)))
  ([s1 s2 & sets]
   (reduce difference s1 (clojure.core/conj sets s2))))

(defn complement [coll]
  (if (empty? coll)
    [(interval (t/min-of-type (t/now)) (t/max-of-type (t/now)))]
    (let [r (map (fn [[x y]] (interval (t/end x) (t/beginning y)))
                 (partition 2 1 coll))]
      (cond-> r
        (not= (t/beginning (first coll)) (t/min-of-type (t/beginning (first coll))))
        (#(concat [(interval (t/min-of-type (t/beginning (first coll))) (t/beginning (first coll)))] %))
        (not= (t/end (last coll)) (t/max-of-type (t/end (last coll))))
        (#(concat % [(interval (t/end (last coll)) (t/max-of-type (t/end (last coll))))]))))))

(defn disjoin
  "Split s1 across the grating defined by s2"
  ([s1] s1)
  ([s1 s2]
   (loop [xs s1
          ys s2
          result []]
     (if (not-empty xs)
       (if (not-empty ys)
         (let [x (first xs) y (first ys)
               code (code (relation x y))]
           (case code
             (\p \m) (recur (next xs) ys (clojure.core/conj result x))
;; TODO:
;;             (\P \M) (recur xs (next ys) result)
;;             (\f \d \e) (recur (next xs) (next ys) result)
;;             \s (recur (next xs) ys result)
;;             (\S \O)
;;             (recur (cons (narrow x (t/end y) (t/end x)) (next xs)) (next ys) result)
;;             \F (recur (next xs) (next ys) (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))
;;            \o (recur (next xs) ys (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))
;;            \D
             #_(recur (cons (narrow x (t/end y) (t/end x)) (next xs))
                    (next ys)
                    (clojure.core/conj result (narrow x (t/beginning x) (t/beginning y))))))
         (apply clojure.core/conj result xs))
       result)))
  ([s1 s2 & sets]
   (reduce difference s1 (clojure.core/conj sets s2))))

;; Division

(defn divide-by
  "Return a lazy sequence of java.time.Temporal instances over the
  given (local) interval."
  [ival f]
  (cond->
      (t/range
        (f (t/beginning ival))
        (f (t/end ival)))
    ;; Since range is exclusive, we must add one more value, but only
    ;; if it concurs rather than merely meets.
    (concur (f (t/end ival)) ival)
    (concat [(f (t/end ival))])))

(defn divide-by-duration
  "Divide an interval by a duration, returning a sequence of
  intervals. If the interval cannot be wholly sub-divided by the
  duration divisor, the last interval will represent the 'remainder'
  of the division and not be as long as the other preceeding
  intervals."
  [ival dur]
  (->> (t/range
         (t/beginning ival)
         (t/end ival)
         dur)
       ;; Bound by given interval, last will become a remainder.
       (map (juxt identity #(t/min (t/forward-duration % dur) (t/end ival))))))

(defn divide-by-period
  [ival period]
  (->> (t/range
         (t/beginning ival)
         (t/end ival)
         period)
       ;; Bound by given interval, last will become a remainder.
       (map (juxt identity #(t/min (t/forward-duration % period) (t/end ival))))))

(defn divide-by-divisor [ival divisor]
  (divide-by-duration ival (.dividedBy (t/duration ival) divisor)))

(defprotocol IDivisibleInterval
  (divide [divisor ival] "Divide an interval by a given divisor"))

(defmulti divide-by-keyword ""
  (fn [ival k] k))

;; TODO: Replace keywords with singular

(defmethod divide-by-keyword :hour [ival _]
  (divide-by-duration ival (t/duration 1 :hours)))

(defmethod divide-by-keyword :minute [ival _]
  (divide-by-duration ival (t/duration 1 :minutes)))

(defmethod divide-by-keyword :date [ival _]
  (divide-by ival t/date))

(defmethod divide-by-keyword :year-month [ival _]
  (divide-by ival t/year-month))

(defmethod divide-by-keyword :year [ival _]
  (divide-by ival t/year))

(extend-protocol IDivisibleInterval
  clojure.lang.Keyword
  (divide [kw ival] (divide-by-keyword ival kw))
  Duration
  (divide [dur ival] (divide-by-duration ival dur))
  Period
  (divide [period ival] (divide-by-period ival period))
  Long
  (divide [divisor ival] (divide-by-divisor ival divisor)))

;; TODO: hours-over, minutes-over, seconds-over, millis-over?,

(extend-protocol t/IDivisible
  LocalDate
  (/ [ld d] (divide d ld))
  Year
  (/ [n d] (divide d n))
  YearMonth
  (/ [n d] (divide d n))
  Interval
  (/ [ival o] (divide o ival)))

;; Grouping (similar to Division)

;; TODO: tag literals data_readers.clj for tick?
;; #tick/instant ""
;; #tick/local-date-time ""
;; #tick/local-date ""

;; TODO: Normalise - splice intervals that touch - opposite of disjoin

(defn group-by-intervals
  "Divide intervals in s1 by (disjoint ordered) intervals in s2,
  splitting if necessary, grouping by s2. Complexity is O(n) rather
  than O(n^2)"
  [s1 s2]
  (loop [intervals s1
         groups s2
         result {}
         current-intervals []]
    (if (not-empty intervals)
      (if (not-empty groups)
        (let [ival (first intervals)
              group (first groups)
              code (code (relation ival group))]

          (case code
            ;; If precedes or meets, discard ival
            (\p \m) (recur (next intervals) groups result current-intervals)
            (\P \M) (recur
                      intervals (next groups)
                      (assoc result group current-intervals)
                      [])

            (\e \f) (recur
                      (next intervals)
                      (next groups)
                      (assoc result group [ival])
                      [])

            (\F) (let [[seg1 seg2] (split-with-assert ival (t/beginning group))]
                   (recur
                     (next intervals)
                     (next groups)
                     (assoc result group [seg2])
                     []))

            (\S) (let [[seg1 seg2] (split-with-assert ival (t/end group))]
                   (recur
                     (cons seg2 (next intervals))
                     (next groups)
                     (assoc result group [seg1])
                     []))

            (\O) (let [[seg1 seg2] (split-with-assert ival (t/end group))]
                   (recur
                     (cons seg2 (next intervals))
                     (next groups)      ; end of this group
                     (assoc result group (clojure.core/conj current-intervals seg1))
                     []))

            (\s \d) (recur
                      (next intervals)
                      groups
                      result
                      (clojure.core/conj current-intervals ival))

            (\D) (recur
                   (next intervals)
                   (next groups)
                   (assoc result group [(narrow ival (t/beginning group) (t/end group))])
                   [])

            (\o) (recur
                   (next intervals)
                   groups
                   result
                   (clojure.core/conj current-intervals (narrow ival (t/beginning group) (t/end ival))))

            (throw (ex-info
                     "Unsupported code"
                     {:code code :ival ival :group group}))))

        ;; No more groups
        result)

      ;; No more intervals
      (cond-> result
        (first groups) (assoc (first groups) current-intervals)
        (not-empty (rest groups))
        ((fn add-remaining-groups [result]
           (reduce (fn [acc group] (assoc acc group [])) result (rest groups))))))))

(defprotocol IGroupable
  (group-by-grouping [grouping ivals]))

(defmulti group-by-keyword "" (fn [ivals k] k))

(defmethod group-by-keyword :year
  [ivals _]
  (let [r (apply bounds ivals)
        b (t/year (t/beginning r))
        e (t/year (t/end r))
        groups (t/range b (t/inc e))]
    (group-by-intervals ivals groups)))

(extend-protocol IGroupable
  clojure.lang.Keyword
  (group-by-grouping [k ivals]
    (group-by-keyword ivals k)))

(defn group-by [ivals grouping]
  (group-by-grouping grouping ivals))
