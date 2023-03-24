(ns sentymenty.fetch-comments
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-http.client :as http-client]))

(def API_KEY
  (or (str/trim (slurp ".env"))
      (System/getenv "YOUTUBE_API_KEY")))

(def comments-api-url
  (str "https://www.googleapis.com/youtube/v3/commentThreads"
       ;; "?part=snippet%2Creplies"
       "?part=snippet,replies"
       "&maxResults=100"
       "&key=" API_KEY
       "&videoId=%s"
       "&pageToken=%s"))

(defn api-call [video-id page-token]
  (let [url (format comments-api-url video-id page-token)]
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

(comment
  (def negative-video-id "YwNj8ZycZMI")
  (def positive-video-id "1Pg1RguhqxY")

  (->> (fetch-comments negative-video-id)
       (clean-comments)
       (dump-comments "negative-sentiment.txt"))

  (->> (fetch-comments positive-video-id)
       (clean-comments)
       (dump-comments "positive-sentiment.txt"))
  )
