(ns sentymenty.core
  (:require
   [clojure.string :as str]
   [clj-http.client :as http-client]))

(def API_KEY
  (or (str/trim (slurp ".env"))
      (System/getenv "YOUTUBE_API_KEY")))

(def comments-api-url
  (str "https://www.googleapis.com/youtube/v3/commentThreads"
       "?part=snippet%2Creplies"
       "&maxResults=100&"
       "&key=" API_KEY
       "&videoId=" "plNJOq9pMnY"
       "&pageToken="))

(defn api-call [page-token]
  (let [url (str comments-api-url page-token)]
    ;; TODO use conn manager
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
                top-level (get-in entity [:topLevelComment :snippet :textOriginal])
                replies (when (pos? (:totalReplyCount entity))
                          (map
                           #(get-in % [:snippet :textOriginal])
                           (get-in entity [:replies :comments])))]
            (if replies
              (conj acc top-level replies)
              (conj acc top-level))))
        [])))

(defn dump-sentences [filename content]
  (with-open [w (clojure.java.io/writer filename)]
    (binding [*out* w]
      (doseq [line content]
        (.write w (str line "\n"))))))

(comment
  (def r
    (into []
          conj
          (iteration api-call {:somef #(= 200 (:status %))
                               :vf extract-comments
                               :kf #(get-in % [:body :nextPageToken])
                               :initk ""
                               }))
    )

  (def rr
    (->> (flatten r)
         (filter string?)))

  (dump-sentences "resources/negative-sentiment.txt" rr)

  )
