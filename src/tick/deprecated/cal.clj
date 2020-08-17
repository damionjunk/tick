;; Copyright © 2016-2017, JUXT LTD.

(ns tick.deprecated.cal
  (:require
   [clojure.spec.alpha :as s]
   [tick.core :as t])
  (:import
   [java.time Clock ZoneId Instant Duration DayOfWeek Month ZonedDateTime LocalDate YearMonth Month MonthDay]
   [java.time.temporal ChronoUnit]))

(s/def ::year int?)
(s/def ::name string?)
(s/def ::date #(instance? LocalDate %))
(s/def ::substitute-day boolean?)
(s/def ::holiday (s/keys :req [::name ::date]
                         :opt [::substitute-day]))

(defn day-of-week
  "Return the day of the week for a given ZonedDateTime"
  [dt]
  (.getDayOfWeek dt))

(defn weekend?
  "Is the ZonedDateTime during the weekend?"
  [dt]
  (#{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (day-of-week dt)))

(defn past? [now]
  (fn [d] (.isBefore d now)))

(defn- first-named-day-from [ld day]
  (first (drop-while #(not= (day-of-week %) day) (t/range ld))))

(defn- last-named-day-from [ld day]
  (first (drop-while #(not= (day-of-week %) day) (t/range ld nil (t/new-period -1 :days)))))

(defn first-monday-of-month [^YearMonth ym]
  (first-named-day-from (.atDay ym 1) DayOfWeek/MONDAY))

(defn last-monday-of-month [^YearMonth ym]
  (last-named-day-from (.atEndOfMonth ym) DayOfWeek/MONDAY))

(defn first-friday-of-month [^YearMonth ym]
  (first-named-day-from (.atDay ym 1) DayOfWeek/FRIDAY))

(defn last-friday-of-month [^YearMonth ym]
  (last-named-day-from (.atEndOfMonth ym) DayOfWeek/FRIDAY))

(defn holiday
  ([name day]
   {:name name
    :date day})
  ([name day hol]
   {:name name
    :date hol
    :substitute-day (and hol (not= day hol))}))

(s/fdef holiday
        :args (s/cat :name ::name :day ::date :hol ::date)
        :ret ::holiday)

(defn new-years-day [year]
  (LocalDate/of (t/int (t/year year)) 1 1))

(defn new-years-day-holiday [year]
  (let [day (new-years-day (t/int (t/year year)))
        hol (cond-> day (weekend? day) (first-named-day-from DayOfWeek/MONDAY))]
    (holiday "New Year's Day" day hol)))

(defn easter-sunday
  "Return a pair containing [month day] of Easter Sunday given the
  year. Copyright © 2016 Eivind Waaler. EPL v1.0. From
  https://github.com/eivindw/clj-easter-day, using Spencer Jones
  formula."
  ;; TODO: From what year does this algorithm makes sense from, need
  ;; to throw an exception outside this range.
  [year]
  (let [year (t/int (t/year year))
        a (mod year 19)
        b (quot year 100)
        c (mod year 100)
        d (quot b 4)
        e (mod b 4)
        f (quot (+ b 8) 25)
        g (quot (+ (- b f) 1) 3)
        h (mod (+ (* 19 a) (- b d g) 15) 30)
        i (quot c 4)
        k (mod c 4)
        l (mod (- (+ 32 (* 2 e) (* 2 i)) h k) 7)
        m (quot (+ a (* 11 h) (* 22 l)) 451)
        n (quot (+ h (- l (* 7 m)) 114) 31)
        p (mod (+ h (- l (* 7 m)) 114) 31)]
    (LocalDate/of year n (+ p 1))))

(defn good-friday [year]
  (.minusDays (easter-sunday year) 2))

(defn good-friday-holiday [year]
  (holiday "Good Friday" (good-friday year)))

(defn easter-monday [year]
  (.plusDays (easter-sunday year) 1))

(defn easter-monday-holiday [year]
  (holiday "Easter Monday" (easter-monday year)))

(defn may-day
  ([]
   (MonthDay/of Month/MAY 1))
  ([year]
   (.atMonthDay (t/year year) (may-day))))

(defn early-may-bank-holiday [year]
  (holiday "Early May bank holiday"
           (first-named-day-from (may-day (t/year year)) DayOfWeek/MONDAY)))

(defn spring-bank-holiday [year]
  (holiday "Spring bank holiday"
           (last-monday-of-month (.atMonth (t/year year) Month/MAY))))

(defn summer-bank-holiday [year]
  (holiday "Summer bank holiday"
           (last-monday-of-month (.atMonth (t/year year) Month/AUGUST))))

(defn christmas-day
  ([]
   (MonthDay/of Month/DECEMBER 25))
  ([year]
   (.atMonthDay (t/year year) (christmas-day))))

(s/fdef christmas-day
        :args (s/cat :year ::year)
        :ret ::date)

(defn christmas-day-holiday [year]
  (let [day (christmas-day (t/year year))
        hol (cond-> day
              (#{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek day)) (.plusDays 2))]
    (holiday "Christmas Day" day hol)))

(s/fdef christmas-day-holiday
        :args (s/cat :year ::year)
        :ret ::holiday)

(defn boxing-day
  ([]
   (MonthDay/of Month/DECEMBER 26))
  ([year]
   (.atMonthDay (t/year year) (boxing-day))))

(s/fdef boxing-day
        :args (s/cat :year ::year)
        :ret ::date)

(defn boxing-day-holiday [year]
  (let [day (boxing-day (t/int (t/year year)))
        hol (cond-> day
              (#{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek day)) (.plusDays 2))]
    (holiday "Boxing Day" day hol)))

(s/fdef boxing-day-holiday
        :args (s/cat :year ::year)
        :ret ::holiday)

(def holidays-in-england-and-wales
  (juxt new-years-day-holiday
        good-friday-holiday
        easter-monday-holiday
        early-may-bank-holiday
        spring-bank-holiday
        summer-bank-holiday
        christmas-day-holiday
        boxing-day-holiday))

;; TODO: Scotland

;; TODO: Northern Ireland

;; TODO: Republic of Ireland

;; TODO: Isle of Man
