(ns rsignals.ta
  (:require
   [incanter.stats :as stats]
   [rsignals.dstats :as dstats]))


(defn take-last-x
  [length xs]
  (let [count-x (count xs)]
    (if (> count-x length)
      (subvec xs (- count-x length))
      xs)))

(defn round
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (when d
    (let [factor (Math/pow 10 precision)]
      (/ (Math/round (* d factor)) factor))))

(defn- extr
  "TODO implementation too complicated"
  [f period coll]
  (->> coll
       (reduce
        (fn [acc curr]
          (let [length (count acc)]
            (if (>= length (dec period))
              (let [xs (take period (conj (:x (last acc)) curr))]
                (if (some #(not (number? %)) xs)
                  (conj acc {:x xs
                             :h (conj (:h (last acc)) nil)})
                  (conj acc {:x xs
                             :h (conj (:h (last acc)) (apply f xs))})))
              (conj acc {:x (conj (:x (last acc)) curr)
                         :h (conj (:h (last acc)) nil)}))))
        [])
       (mapv :h)
       peek
       reverse
       ;; TODO get rid of vec here
       vec))

(defn lowest
  [period coll]
  (extr min period coll))

(defn highest
  [period coll]
  (extr max period coll))

(comment
  (every? number? [1 2.0])

  (let [d [nil
           nil
           nil
           2.77881995E15
           4.25885904E14]
        period (* 3 3)]
    (highest period d))

  (highest 2 [9 2 3 4 12 4 5 6])
  (lowest 2 [-9 -2 -3 -4 -12 -4 -5 -6])
  1)

(defn mean
  [xs]
  (float
   (/ (reduce + xs)
      (count xs))))

(defn exp [x n]
  (when x (reduce * (repeat n x))))

(defn sma
  "baseline"
  [period xs]
  (reduce
   (fn [acc _]
     (let [length (count acc)
           step (- length period)
           sum (when (nat-int? step)
                 (let [slice (subvec xs (inc step) (inc length))]
                   (when-not (some #(not (number? %)) slice)
                     (mean slice))))]
       (conj acc sum)))
   []
   xs))

(defn rma
  "baseline"
  [sources period]
  (let [a (/ 1 period)
        a* (- 1 a)]
    (->> sources
         (reduce
          (fn [acc source]
            (let [length (count acc)]
              (if (zero? length)
                (conj acc {:source source
                           :sum (mean (subvec sources 0 period))})
                (let [sum-prev (:sum (peek acc))]
                  (conj acc  {:source source
                              :sum (+ (* a source) (* a* sum-prev))})))))
          [])
         (mapv #(:sum %)))))

(defn tr
  [coll]
  (->> coll
       (reduce-kv
        (fn [acc idx {:keys [high close low]}]
          (let [high-low (- high low)]
            (if (zero? idx)
              (conj acc {:tr high-low :close-prev close})
              (let [close-prev (-> acc peek :close-prev)
                    tr (max
                        high-low
                        (Math/abs (- high close-prev))
                        (Math/abs (- low close-prev)))]
                (conj acc {:tr tr :close-prev close})))))
        [])
       (mapv #(:tr %))))

(defn atr
  [period coll]
  (let [length (count coll)]
    (if (> period length)
      (vec (repeat length nil))
      (-> coll
          tr
          (rma period)))))

(defn ema
  "baseline"
  [period sources]
  (let [a (/ 2 (inc period))
        a' (- 1 a)]
    (->> sources
         (reduce-kv
          (fn [acc idx curr]
            (let [ema-y (-> acc peek :ema)]
              (if (or (= idx 0) (nil? ema-y))
                (conj acc {:ema curr})
                (if (and curr ema-y)
                  (conj acc {:ema (float (+ (* curr a) (* ema-y a')))})
                  (conj acc {:ema curr})))))
          [])
         (mapv :ema))))

(defn rsi
  "This is really slow!!!"
  [period sources]
  (->> sources
       (reduce
        (fn [acc c]
          (let [source-prev (:source (peek acc))
                length (count acc)
                change (if source-prev (- c source-prev) 0)
                u (max change 0)
                d (- (min change 0))
                up (when (> length period)
                     (as-> acc coll
                       (mapv #(:u %) coll)
                       (conj coll u)
                       (rma coll period)
                       (peek coll)))
                down (when (> length period)
                       (as-> acc coll
                         (mapv #(:d %) coll)
                         (conj coll d)
                         (rma coll period)
                         (peek coll)))
                rsi (cond
                      (nil? down) nil
                      (nil? up) nil
                      (zero? down) 100
                      (zero? up) 0
                      :else (- 100 (/ 100 (inc (/ up down)))))]
            (conj acc {:source c
                       :u u
                       :d d
                       :rsi rsi})))
        [])
       (mapv #(:rsi %))))

(defn rocp
  [period sources]
  (reduce
   (fn [acc curr]
     (let [length-acc (count acc)
           past (nth sources (- length-acc period) nil)]
       (if past
         (conj acc (float (* (/ (- curr past) past) 100)))
         (conj acc nil))))
   []
   sources))

(defn- minus-prev
  "sources - sources[1]"
  [sources]
  (->> sources
       (reduce-kv
        (fn [acc idx curr]
          (if (zero? idx)
            (conj acc {:v curr})
            (let [prev (-> acc peek :v)]
              (conj acc {:v curr :sum (when prev (- curr prev))}))))
        [])
       (mapv #(:sum %))))

(defn tdfi [period sources]
  (let [mma (ema period (mapv #(* 1 %) sources))
        smma (ema period mma)
        impetmma (minus-prev mma)
        impetsmma (minus-prev smma)
        divma (mapv
               #(when (and %1 %2)
                  (Math/abs (- %1 %2)))
               mma smma)
        averimpet (mapv #(when (and %1 %2) (/ (+ %1 %2) 2)) impetmma impetsmma)
        result (mapv #(exp % 3) averimpet)
        tdf (mapv #(when (and %1 %2) (* %1 %2)) divma result)
        atdf (mapv #(when % (Math/abs %)) tdf)
        htdf (highest (* period 3) atdf)
        ntdf (mapv
              #(when (and %1 %2) (round 6 (/ %1 %2)))
              tdf
              htdf)]
    ntdf))

(defn smoothed-tdfi [speriod period sources]
  (sma speriod (tdfi period sources)))

(defn esmoothed-tdfi [speriod period sources]
  (ema speriod (tdfi period sources)))

(defn donchian
  "baseline"
  [period coll]
  (mapv
   (fn [low, high]
     (when (and low high)
       (/ (+ low high) 2)))
   (lowest period (mapv :low coll))
   (highest period (mapv :high coll))))

(defn wma
  "baseline"
  [period sources]
  (->> sources
       (reduce
        (fn [acc curr]
          (let [length (count acc)]
            (if (>= length (dec period))
              (let [xs (take period (conj (:x (last acc)) curr))]
                (if (some #(not (number? %)) xs)
                  (conj acc {:x xs :wma (conj (:wma (last acc)) nil)})
                  (let [div (/ (* period (inc period)) 2)
                        a (/
                           (apply
                            +
                            (map-indexed
                             (fn [i x]
                               (* x (* (- period i))))
                             xs))
                           div)]
                    (conj acc {:x xs :wma (conj (:wma (last acc)) a)}))))
              (conj acc {:x (conj (:x (last acc)) curr)
                         :wma (conj (:wma (last acc)) nil)}))))
        [])
       (map :wma)
       last
       reverse))

(defn tsi
  "pr > ps
  https://en.wikipedia.org/wiki/True_strength_index
  https://stackoverflow.com/questions/5734435/put-an-element-to-the-tail-of-a-collection
  https://stackoverflow.com/questions/24496810/whats-the-idiomatic-way-to-keep-track-of-previous-values-in-clojure"
  [pr ps sources]
  (->> sources
       (reduce
        (fn [acc curr]
          (let [length (count (:m acc))]
            (if (zero? length)
              {:tsi [nil]
               :m [nil]
               :m* [nil]
               :prev curr}
              (let [prev (:prev acc)
                    m (- curr prev)
                    m* (Math/abs m)
                    ms (take-last-x 300 (conj (:m acc) m))
                    m*s (take-last-x 300 (conj (:m* acc) m*))]
                (if (< length pr)
                  {:tsi (conj (:tsi acc) nil)
                   :m ms
                   :m* m*s
                   :prev curr}
                  (let [ema-m  (peek (ema ps (ema pr ms)))
                        ema-m* (peek (ema ps (ema pr m*s)))
                        tsi* (when (and ema-m ema-m*)
                               (if (not (zero? ema-m*))
                                 (* 100 (/ ema-m ema-m*))
                                 0))]
                    {:tsi (conj (:tsi acc) tsi*)
                     :m ms
                     :m* m*s
                     :prev curr}))))))
        {:m []})
       :tsi))

(defn tenkan
  "baseline"
  [period sources]
  (->> sources
       (reduce
        (fn [acc curr]
          (let [length (count (:sources acc))]
            (if (< length period)
              {:tenkan (conj (:tenkan acc) nil)
               :sources (conj (:sources acc) curr)}
              (let [xs (take-last-x period (conj (:sources acc) curr))
                    tenkan (* (+ (peek (highest period xs)) (peek (lowest period xs))) 0.5)]
                {:tenkan (conj (:tenkan acc) tenkan)
                 :sources xs}))))
        {:sources []
         :tenkan []})
       :tenkan))

(defn rex-tenkan
  "uses sma, possible tenkan"
  [period sources]
  (->> sources
       (reduce
        (fn [acc curr]
          (let [length (count (:tvb acc))
                {:keys [close open high low]} curr
                tvb (- (- (- (* 3 close) open) high) low)
                tvbs (take-last-x 400 (conj (:tvb acc) tvb))]
            (if (< length period)
              {:rex (conj (:rex acc) nil)
               :tvb tvbs}
              (let [rex (peek (tenkan period tvbs))]
                {:rex (conj (:rex acc) rex)
                 :tvb tvbs}))))
        {:tvb []
         :rex []})
       :rex))

(defn rex-sma
  [period sources]
  (->> sources
       (reduce
        (fn [acc curr]
          (let [length (count (:tvb acc))
                {:keys [close open high low]} curr
                tvb (- (- (- (* 3 close) open) high) low)
                tvbs (take-last-x 400 (conj (:tvb acc) tvb))]
            (if (< length period)
              {:rex (conj (:rex acc) nil)
               :tvb tvbs}
              (let [rex (peek (sma period tvbs))]
                {:rex (conj (:rex acc) rex)
                 :tvb tvbs}))))
        {:tvb []
         :rex []})
       :rex))

(defn ema2 [period sources]
  (let [alpha (/ 2 (+ period 1))]
    (map-indexed
     (fn [idx src]
       (if (or (< idx period) (nil? src))
         nil
         (let [prev (nth sources (dec idx))]
           (float (+ (* alpha src) (* (- 1 alpha) prev)))
           (+ (* alpha src) (* (- 1 alpha) prev)))))
     sources)))

(defn ema1 [period sources]
  (let [alpha (/ 2 (+ period 1))]
    (reduce
     (fn [acc curr]
       (if (< (count acc) period)
         (conj acc nil)
         (let [prev* (peek acc)
               prev (if (nil? prev*)
                      curr
                      prev*)
               sum (float (+ (* alpha curr) (* (- 1 alpha) prev)))]
           (conj acc sum))))
     []
     sources)))
(defn ema3 [period sources]
  (let [a (/ 2 (+ period 1))]
    (reduce
     (fn [acc src]
       (if (nil? src)
         (conj acc nil)
         (conj
          acc
          (+ (* a src)
             (* (- 1 a)
                (or (peek acc) 0.0))))))
     [(first sources)]
     (rest sources))))

(defn hull
  "baseline"
  [period xs]
  (let [wma-half (wma (round 0 (/ period 2)) xs)
        wma-full (wma period xs)
        wma-mod (mapv
                 (fn [h, f]
                   (if (and h f)
                     (- (* 2 h) f)
                     nil))
                 wma-half
                 wma-full)
        h-period (Math/floor (Math/sqrt period))]
    (wma h-period wma-mod)))

(defn- max-back
  [xs]
  (let [length (dec (count xs))]
    (->> xs
         (reduce-kv
          (fn [acc idx x]
            (if (zero? idx)
              {:m x :i (- length idx)}
              (let [m-prev (:m acc)]
                (if (> x m-prev)
                  {:m x :i (- length idx)}
                  acc))))
          {})
         :i)))

(defn- min-back
  [xs]
  (let [length (dec (count xs))]
    (->> xs
         (reduce-kv
          (fn [acc idx x]
            (if (zero? idx)
              {:m x :i (- length idx)}
              (let [m-prev (:m acc)]
                (if (< x m-prev)
                  {:m x :i (- length idx)}
                  acc))))
          {})
         :i)))

(defn aroon-up
  [period sources]
  (let [req-period (inc period)]
    (->> sources
         (reduce-kv
          (fn [acc idx x]
            (if (< idx req-period)
              {:p (conj (:p acc) x)
               :au (conj (:au acc) nil)}
              (let [ps (conj (:p acc) x)
                    slice (take-last-x req-period ps)
                    max-b (max-back slice)
                    au (float (* (/ (- period max-b) period) 100))]
                {:p ps
                 :au (conj (:au acc) au)})))
          {:p []
           :au []})
         :au)))

(defn aroon-down
  [period sources]
  (let [req-period (inc period)]
    (->> sources
         (reduce-kv
          (fn [acc idx x]
            (if (< idx req-period)
              {:p (conj (:p acc) x)
               :au (conj (:au acc) nil)}
              (let [ps (conj (:p acc) x)
                    slice (take-last-x req-period ps)
                    min-b (min-back slice)
                    au (float (* (/ (- period min-b) period) 100))]
                {:p ps
                 :au (conj (:au acc) au)})))
          {:p []
           :au []})
         :au)))

(defn larry-williams-proxy
  [period sources]
  (let [ocs (mapv #(- (:open %) (:close %)) sources)
        socs (sma period ocs)
        atrs (atr period sources)]
    ;; (prn (count socs) (count atrs))
    (mapv
     (fn [ma atr*]
       (if (and ma atr*)
         ; 50*ma/atr+50
         (+ (* 50 (/ ma atr*)) 50)
         nil))
     socs
     atrs)))

(defn didi-sma
  [p-mid p-large sources]
  (let [p-mids (sma p-mid sources)
        p-largs (sma p-large sources)]
    (mapv
     (fn [l m]
       (if (and l m)
         ; sma (close, long) - sma (close, mid)
         (- l m)
         nil))
     p-largs
     p-mids)))

(defn stdev*
  "Little different"
  [mult period xs]
  (map-indexed
   (fn [i _]
     (let [step (dec period)]
       (if (< i step)
         nil
         (let [slice (subvec xs (- i step) (inc i))]
           (* (stats/sd slice) mult)))))
   xs))

(defn stdev
  "Same results as pinescript"
  [mult period xs]
  (map-indexed
   (fn [i _]
     (let [step (dec period)]
       (if (< i step)
         nil
         (let [slice (subvec xs (- i step) (inc i))]
           (* (dstats/standard-deviation slice) mult)))))
   xs))

(defn bbp
  [mult period xs]
  (let [devs (stdev mult period xs)
        basis (sma period xs)]
    (mapv
     (fn [x dev base]
       (if (and dev base x)
         (let [upper (+ base dev)
               lower (- base dev)]
           (/ (- x lower) (- upper lower)))
         nil))
     xs
     devs
     basis)))

(defn ssl*-ema
  [period coll]
  (let [highs (mapv :high coll)
        lows (mapv :low coll)
        closes (mapv :close coll)
        ma-highs (ema period highs)
        ma-lows (ema period lows)]
    (reduce-kv
     (fn [acc idx c0]
       (let [h0 (nth ma-highs idx)
             l0 (nth ma-lows idx)]
         (if (and (>= idx period) c0 h0 l0)
           (cond
             (> c0 h0) (conj acc 1)
             (< c0 l0) (conj acc -1)
             :else (conj acc (nth acc (dec idx) nil)))
           (conj acc nil))))
     []
     closes)))

(defn vwma
  [period source-kw coll]
  (let [closes (mapv source-kw coll)
        volumes (mapv :volume coll)
        sma-volumes (sma period volumes)
        close-volume (mapv #(* %1 %2) closes volumes)
        sma-c-v (sma period close-volume)]
    (mapv
     (fn [cv v]
       (if (and cv v)
         (/ cv v)
         nil))
     sma-c-v
     sma-volumes)))

(defn braid-ema
  [pipsp period1 period2 period3 coll]
  (let [closes (mapv :close coll)
        opens (mapv :open coll)
        mas1 (ema period1 closes)
        mas2 (ema period2 opens)
        mas3 (ema period3 closes)
        atrs (atr 14 coll)]
    (map
     (fn [ma1 ma2 ma3 atr-v]
       (if (and ma1 ma2 ma3 atr-v)
         (let [max-v (max (max ma1 ma2) ma3)
               min-v (min (min ma1 ma2) ma3)
               dif (- max-v min-v)
               filter (/ (* atr-v pipsp) 100)
               ; ma01 > ma02 and dif > filter
               long? (and (> ma1 ma2)
                          (> dif filter))
               ; ma02 > ma01 and dif > filter
               short? (and (> ma2 ma1)
                           (> dif filter))]
           (cond
             long? 1
             short? -1
             :else 0))
         nil))
     mas1
     mas2
     mas3
     atrs)))

(defn braid-vwma
  [pipsp period1 period2 period3 coll]
  (let [mas1 (vwma period1 :close coll)
        mas2 (vwma period2 :open coll)
        mas3 (vwma period3 :close coll)
        atrs (atr 14 coll)]
    (map
     (fn [ma1 ma2 ma3 atr-v]
       (if (and ma1 ma2 ma3 atr-v)
         (let [max-v (max (max ma1 ma2) ma3)
               min-v (min (min ma1 ma2) ma3)
               dif (- max-v min-v)
               filter (/ (* atr-v pipsp) 100)
               long? (and (> ma1 ma2)
                          (> dif filter))
               short? (and (> ma2 ma1)
                           (> dif filter))]
           (cond
             long? 1
             short? -1
             :else 0))
         nil))
     mas1
     mas2
     mas3
     atrs)))
