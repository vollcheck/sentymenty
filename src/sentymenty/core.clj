{:nextjournal.clerk/visibility {:code :show :result :hide}}
(ns sentymenty.core
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-http.client :as http-client]))

;;
;; Define API key needed for accessing YouTube data:
(def API_KEY
  (or (str/trim (slurp ".env"))
      (System/getenv "YOUTUBE_API_KEY")))

;;
;; Coin the URL for comments API requests:
(def comments-api-url
  (str "https://www.googleapis.com/youtube/v3/commentThreads"
       "?part=snippet,replies"
       "&maxResults=100"
       "&key=" API_KEY
       "&videoId=%s"
       "&pageToken=%s"))

(defn api-call [video-id page-token]
  (let [url (format comments-api-url video-id page-token)]
    (http-client/get url {:accept :json
                          :as :json
                          :cache true})))

(defn extract-comments
  "Giving the response for max results (100) extract the comment strings"
  [response]
  (->> (get-in response [:body :items])
       (reduce
        (fn [acc entity]
          (let [entity (:snippet entity)
                remove-ws (fn [s] (str/replace s #"\n" " "))
                top-level (remove-ws
                           (get-in entity [:topLevelComment :snippet :textOriginal]))
                replies (when (pos? (:totalReplyCount entity))
                          (map
                           #(remove-ws (get-in % [:snippet :textOriginal]))
                           (get-in entity [:replies :comments])))]
            (if replies
              (conj acc top-level replies)
              (conj acc top-level))))
        [])))

(defn fetch-comments
  "General abstraction for handling with paged API responses"
  [video-id]
  (into []
        conj
        (iteration (partial api-call video-id)
                   {:somef #(= 200 (:status %))
                    :vf extract-comments
                    :kf #(get-in % [:body :nextPageToken])
                    :initk ""})))

(defn clean-comments [comments]
  (->> comments
       (flatten)
       (filter string?)))

(defn dump-comments [filename content]
  (with-open [w (io/writer filename)]
    (binding [*out* w]
      (doseq [line content]
        (.write w (str line "\n"))))))

;; Negative video comments fetch and save

(def negative-video-id "YwNj8ZycZMI")

(->> (fetch-comments negative-video-id)
     (clean-comments)
     (dump-comments "resources/negative-sentiment.txt"))

;; Positive video comments fetch and save

(def positive-video-id "1Pg1RguhqxY")

(->> (fetch-comments positive-video-id)
     (clean-comments)
     (dump-comments "resources/positive-sentiment.txt"))


;; ## Loading needed libraries for Python and data processing
(require
 '[libpython-clj2.require :refer [require-python]]
 '[libpython-clj2.python :as py :refer [py. py.. py.-]]
 '[kixi.stats.core :refer [mean]])

(require-python '[spacy :as spacy]
                '[spacytextblob.spacytextblob.SpacyTextBlob :as SpacyTextBlob])

(def nlp
  (let [nlp (spacy/load "en_core_web_sm")]
    (py. nlp add_pipe "spacytextblob")
    nlp))

(defn text->polarity [nlp' text]
  (py.. (nlp' text)
        -_ -blob -polarity)) ;; it's nlp(text)._.blob.polarity in Python

(def pos-data
  (-> "resources/positive-sentiment.txt"
      slurp
      str/split-lines))

;; Look into examplary data:
{:nextjournal.clerk/visibility {:code :show :result :show}}
(nth pos-data 401)

;; Checking it's polarity:
(text->polarity nlp (nth pos-data 401))

(def pos-coll
  (mapv (partial text->polarity nlp) pos-data))

(transduce identity mean pos-coll)

{:nextjournal.clerk/visibility {:code :show :result :hide}}

(def pos-freqs (frequencies pos-coll))

(require '[nextjournal.clerk.viewer :as v])

;; ## Sentiment distribution histogram for the positive video

{:nextjournal.clerk/visibility {:code :show :result :show}}
(v/plotly {:data [{:x (keys pos-freqs) :y (vals pos-freqs)
                   :type "histogram"}]})

;; Let's do the same with negative video
(def neg-data
  (-> "resources/negative-sentiment.txt"
      slurp
      str/split-lines))

;; Look into examplary data:
{:nextjournal.clerk/visibility {:code :show :result :show}}
(nth neg-data 10)

;; Checking it's polarity:
(text->polarity nlp (nth neg-data 10))

(def neg-coll
  (mapv (partial text->polarity nlp) neg-data))

(transduce identity mean neg-coll)

{:nextjournal.clerk/visibility {:code :show :result :hide}}
(def neg-freqs (frequencies neg-coll))

;; ## Sentiment distribution histogram for the negative video
{:nextjournal.clerk/visibility {:code :show :result :show}}
(v/plotly {:data [{:x (keys neg-freqs) :y (vals neg-freqs)
                   :type "histogram"}]})

{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment
  (def pos-coll (load-and-compute nlp
                                  "resources/positive-sentiment.txt"))

  (def neg-coll (load-and-compute nlp
                                  "resources/negative-sentiment.txt"))

  (transduce identity mean pos-coll)
   ;; => 0.14902176846455445

  (transduce identity mean neg-coll)
   ;; => -0.013954982722558225
  )

(defn get-thumbnail [video-id]
  (-> "https://www.googleapis.com/youtube/v3/videos?key=%s&part=snippet&id=%s"
      (format API_KEY video-id)
      (http-client/get  {:accept :json
                         :as :json
                         :cache true})
      :body :items first :snippet :thumbnails :high :url))

(def negative-thumbnail (get-thumbnail negative-video-id))
(def positive-thumbnail (get-thumbnail positive-video-id))

(import '(javax.imageio ImageIO))
(import '(java.net URL))
(ImageIO/read (URL. negative-thumbnail))
(ImageIO/read (URL. positive-thumbnail))
