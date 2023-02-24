(ns rsignals.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [clj-http.client :as client]
            [clojure.core.async :as async]
            [cheshire.core :as json]))

(defn get-request
  [url]
  (let [res (client/get
             url
             {:headers {:content-type "application/json"}
              :throw-entire-message? true})]
    (json/parse-string (:body res) true)))

(defn time-now
  []
  (java.util.Date.))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(defn time-page
  [request]
  (ring-resp/response (str "Ahoi " (time-now))))

(defn post-request-with-body-json
  [url body]
  (let [res (client/post
             url
             {:headers {:content-type "application/json"}
              :body (json/generate-string body)
              :throw-entire-message? true})]
    (json/parse-string (:body res) true)))

(defn send-signal
  [request]
  (let [url (if (System/getenv "APP_DOCKER")
              "http://njs:3000/"
              "http://0.0.0.0:3000/")
        res (post-request-with-body-json
             url
             {:signal "test"
              :data "test data"})]
    (ring-resp/response (str "Reqed " url " " (time-now) " " res))))

(comment
  (let [url (if (System/getenv "APP_DOCKER")
              "http://njs:3000/"
              "http://0.0.0.0:3000/signal")]
    (post-request-with-body-json
     url
     {:signal "test"
      :data "test data"}))

  1)

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/time" :get (conj common-interceptors `time-page)]
              ["/signal" :get (conj common-interceptors `send-signal)]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by rsignals.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; accept incoming connections
              ::http/host "0.0.0.0"
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        ;; Alternatively, You can specify your own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        }})


(defn getTimeInUTC
  []
  (let [date (java.util.Date.)
        tz (java.util.TimeZone/getTimeZone "UTC")
        cal (java.util.Calendar/getInstance tz)]
    (.setTime cal date)
    {:h (.get cal java.util.Calendar/HOUR_OF_DAY)
     :m (.get cal java.util.Calendar/MINUTE)
     :s (.get cal java.util.Calendar/SECOND)}))

(def run-engine (atom true))

(defn engine
  []
  (prn (getTimeInUTC))
  (let [url (if (System/getenv "APP_DOCKER")
              "http://njs:3000/signal"
              "http://0.0.0.0:3000/signal")]
    (post-request-with-body-json
     url
     {:signal "test"
      :data "test data"}))

  (Thread/sleep 1000)
  (when @run-engine (recur)))

(defn start-worker
  []
  (reset! run-engine true)
  (async/thread (engine)))


(comment

  (start-worker)





  (reset! run-engine false)

  (prn @run-engine)

  (TimeZone/getTimeZone "GMT")

  (System/currentTimeMillis)
  (quot (System/currentTimeMillis) 1000)

  (.format (java.text.SimpleDateFormat. "MM/dd/yyyy hh") (new java.util.Date))

  (getTimeInUTC)


  1)


