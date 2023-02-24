(ns rsignals.ohlc
  (:require
   [clj-http.client :as client]
   [cheshire.core :as json]
   [rsignals.ta :as ta]))

(def bound-values {"1h" 37
                   "4h" 10
                   "1d" 3})

(defn with-indicators
  [xs]
  (mapv
   (fn [d atr tdfi-l]
     (let [tdfi-l (when tdfi-l (float tdfi-l))]
      (merge d {:atr atr :tdfi-l tdfi-l})))
   xs
   (ta/atr 14 (vec xs))
   (ta/tdfi 11 (mapv :close xs))))

(defn get-request
  [url]
  (let [res (client/get
             url
             {:headers {:content-type "application/json"}
              :throw-entire-message? true})]
    (json/parse-string (:body res) true)))

(defn prep-data
  [ticker interval coll]
  (->> coll
       (mapv
        (fn [[start open high low close volume end]]
          {:time start
           :startTime (str (java.util.Date. start))
           :open (Double/parseDouble open)
           :high (Double/parseDouble high)
           :low (Double/parseDouble low)
           :close (Double/parseDouble close)
           :volume (Double/parseDouble volume)
           :end end
           :end* (str (java.util.Date. end))
           :market ticker
           :resolution interval
           :exchange "B"}))
       (group-by :time)
       vals
       (mapv first)
       (sort-by :time)
       with-indicators
       (drop-last)))

(defn fin-data
  [ticker interval coll]
  (->> coll
       (prep-data ticker interval)
       (drop-last)))

(defn paginate-spot [ticker interval i bound-v endtime coll]
  (if (< i bound-v)
    (do
      (when (> i 1) (Thread/sleep 333))
      (let [url (if (nil? endtime)
                  (format
                   "https://api.binance.com/api/v3/klines?limit=1000&symbol=%s&interval=%s"
                   ticker
                   interval)
                  (format
                   "https://api.binance.com/api/v3/klines?limit=1000&symbol=%s&interval=%s&endTime=%d"
                   ticker
                   interval
                   endtime))
            data (get-request url)
            coll (concat data coll)
            endtime (if (-> data count zero?)
                      false
                      (->> data
                           (mapv #(first %))
                           (apply min)
                           dec))]
        (prn url)
        (if endtime
          (recur ticker interval (inc i) bound-v endtime coll)
          (fin-data ticker interval coll))))
    (fin-data ticker interval coll)))

(defn write-results-edn
  [f-name xs]
  (spit (str "resources/db/data/" f-name ".edn") (pr-str xs)))

(defn save-data
  [interval tickers]
  (let [bound-v (get bound-values interval)]
    (doall
     (map
      (fn [ticker]
        (let [res (paginate-spot ticker interval 1 bound-v nil [])]
          (prn ticker interval (count res))
          (write-results-edn
           (str ticker "_spot" "_" interval)
           res)))
      tickers))))

(defn read-results-edn
  [f-name]
  (read-string (slurp (str "resources/db/data/" f-name ".edn"))))



(comment

  (save-data
   "4h"
   ["BTCUSDT"
    "ETHUSDT"
    "ADAUSDT"
    "BNBUSDT"])

  ;; ; test
  ;; (let [interval "1d"
  ;;       bound-v 2
  ;;       ticker "BTCUSDT"
  ;;       res (paginate-spot ticker interval 1 bound-v nil [])]
  ;;   (clojure.pprint/pprint res))

  (read-results-edn "BTCUSDT_spot_4h")

  (let [res (paginate-spot "BTCUSDT" "4h" 1 2 nil [])]
    (clojure.pprint/pprint res)
    (count res))


  1)

(defn get-ohcl-from-bybit-v3
  [ticker interval]
  (let [url (format
             "https://api.bybit.com/v2/public/kline/list?symbol=%s&interval=%s&limit=1000"
             ticker
             interval)
        data (get-request url)
        coll (->> data
                  :result
                  :data)]
    (prep-data ticker interval coll)))

(defn get-ohcl-from-bybit-v2
  "Kline interval. 1,3,5,15,30,60,120,240,360,720,D,M,W"
  [ticker interval]
  (let [url (format
             "https://api.bybit.com/v5/market/kline?category=linear&symbol=%s&interval=%s&limit=1000"
             ticker
             interval)
        data (get-request url)
        coll (->> data
                  :result
                  :list
                  (sort-by first)
                  (mapv
                   (fn [[start open high low close volume]]
                     {:time start
                      :startTime (str (java.util.Date. (Long/valueOf start)))
                      :open (Double/parseDouble open)
                      :high (Double/parseDouble high)
                      :low (Double/parseDouble low)
                      :close (Double/parseDouble close)
                      :volume (Double/parseDouble volume)
                      :market ticker
                      :resolution interval
                      :exchange "BB"}))
                  with-indicators)]
    coll))

(comment

  (let [d (get-ohcl-from-bybit-v2 "BTCUSD" "240")]
    (clojure.pprint/pprint d)
    (count d))


  1)

; create loop 
