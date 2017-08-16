;; Copyright © 2016-2017, JUXT LTD.

(ns tick.alpha.api
  (:refer-clojure :exclude [+ - inc dec max min range time int long])
  (:require
   [clojure.spec.alpha :as s]
   [tick.core :as core]
   [tick.cal :as cal]
   [tick.interval :as interval]
   [clojure.set :as set])
  (:import
   [java.time Duration ZoneId LocalTime LocalDate DayOfWeek Month]))

;; This API is optimises convenience, API stability and (type) safety
;; over performance. Where performance is critical, use tick.core and
;; friends.

;; clojure.spec assertions are used to check correctness, but these
;; are disabled by default (except when testing).

;; Fixing the clock used for `today` and `now`.

(defmacro with-clock [^java.time.Clock clock & body]
  `(binding [tick.core/*clock* ~clock]
     ~@body))

;; Point-in-time 'demo' functions

(defn now [] (core/now))
(defn today [] (core/today))
(defn tomorrow [] (core/tomorrow))
(defn yesterday [] (core/yesterday))

;; Constants

(def monday DayOfWeek/MONDAY)
(def tuesday DayOfWeek/TUESDAY)
(def wednesday DayOfWeek/WEDNESDAY)
(def thursday DayOfWeek/THURSDAY)
(def friday DayOfWeek/FRIDAY)
(def saturday DayOfWeek/SATURDAY)
(def sunday DayOfWeek/SUNDAY)

(def january Month/JANUARY)
(def february Month/FEBRUARY)
(def march Month/MARCH)
(def april Month/APRIL)
(def may Month/MAY)
(def june Month/JUNE)
(def july Month/JULY)
(def august Month/AUGUST)
(def september Month/SEPTEMBER)
(def october Month/OCTOBER)
(def november Month/NOVEMBER)
(def december Month/DECEMBER)

;; Constructors

(defn date
  ([] (core/date (today)))
  ([v] (core/date v)))
(defn day
  ([] (core/day (today)))
  ([v] (core/day v)))
(defn inst
  ([] (core/inst (now)))
  ([v] (core/inst v)))
(defn instant
  ([] (core/instant (now)))
  ([v] (core/instant v)))
(defn offset-date-time
  ([] (core/offset-date-time (now)))
  ([v] (core/offset-date-time v)))
(defn month
  ([] (core/month (today)))
  ([v] (core/month v)))
(defn year
  ([] (core/year (today)))
  ([v] (core/year v)))
(defn year-month
  ([] (core/year-month (today)))
  ([v] (core/year-month v)))
(defn zone [z] (core/zone z))
(defn zoned-date-time [z] (core/zoned-date-time z))

;; Time

(defn time
  ([] (core/time (now)))
  ([v] (core/time v)))
(defn on [t d] (core/on (time t) (date d)))
(defn at [d t] (core/at (date d) (time t)))
(defn start [v] (core/start v))
(defn end [v] (core/end v))
(defn midnight? [v] (core/midnight? v))

;; Zones

(defn at-zone [t z]
  (core/at-zone t (zone z)))

(defn to-local
  ([v] (core/to-local v))
  ([v z] (core/to-local v (zone z))))

(def UTC (zone "UTC"))
(def LONDON (zone "Europe/London"))

;; Arithmetic

(defn +
  ([] Duration/ZERO)
  ([arg] arg)
  ([arg & args]
   (reduce #(core/+ %1 %2) arg args)))

(defn - [arg & args]
  (reduce #(core/- %1 %2) arg args))

(defn inc [arg]
  (core/inc arg))

(defn dec [arg]
  (core/dec arg))

(defn max [arg & args]
  (reduce #(core/max %1 %2) arg args))

(defn min [arg & args]
  (reduce #(core/min %1 %2) arg args))

(def range core/range)

(defn int [arg] (core/int arg))
(defn long [arg] (core/long arg))

;; Durations

(defn nanos [v] (core/nanos v))
(defn millis [v] (core/millis v))
(defn seconds [v] (core/seconds v))
(defn minutes [v] (core/minutes v))
(defn hours [v] (core/hours v))
(defn days [v] (core/days v))
(defn weeks [v] (core/weeks v))
(defn months [v] (core/months v))
(defn years [v] (core/years v))

;; Intervals

(defn interval
  "Return an interval which forms the bounding-box of the given arguments."
  ([v] (interval/interval v))
  ([v1 & args] (apply interval/interval v1 args)))

;; An interval is just a vector with at least 2 entries. The 3rd entry
;; onwards are free to use by the caller.
(defn interval? [v] (and (vector? v) (>= (count v) 2)))

(defn duration [interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/duration interval)))

(defn concur [x y]
  (interval/concur x y))

;; Useful functions

(defn dates-over [interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/dates-over interval)))

(defn year-months-over [interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/year-months-over interval)))

(defn years-over [interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/years-over interval)))

;; Note: Not sure about partition here for an individual interval. Should reserve for interval sets.

(defn segment-by [f interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/segment-by f interval)))

(defn segment-by-date [interval]
  (segment-by interval/dates-over interval))

(defn group-segments-by [f interval]
  (let [interval (interval/interval interval)]
    (s/assert :tick.interval/interval interval)
    (interval/group-segments-by f interval)))

(defn group-segments-by-date [interval]
  (group-segments-by interval/dates-over interval))

;; Interval sets

(defn ordered-disjoint-intervals?
  "An interval set is an ordered collection of disjoint
  intervals. This predicate can be used to ensure a value conforms to
  this definition, because some functions depend on this property
  holding."
  [s]
  (interval/ordered-disjoint-intervals? s))

(defn union [& colls]
  "Return a set that is the union of the input interval sets"
  {:pre [(s/assert (s/coll-of ordered-disjoint-intervals?) colls)]}
  (apply interval/union colls))

(defn difference
  "Return a set that is the intersection of the input interval sets"
  ([s1] s1)
  ([s1 s2]
   {:pre [(s/assert ordered-disjoint-intervals? s1)
          (s/assert ordered-disjoint-intervals? s2)]}
   (interval/difference s1 s2))
  ([s1 s2 & sets]
   (difference s1 (apply union s2 sets))))
