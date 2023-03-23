(ns sentymenty.core
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

(def negative-video-id "plNJOq9pMnY")
(def positive-video-id "2DiP0mMeaT8")

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
                top-level (get-in entity [:topLevelComment :snippet :textOriginal])
                replies (when (pos? (:totalReplyCount entity))
                          (map
                           #(get-in % [:snippet :textOriginal])
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

(defn dump-sentences [filename content]
  (with-open [w (io/writer filename)]
    (binding [*out* w]
      (doseq [line content]
        (.write w (str line "\n"))))))

(defn clean-comments [comments]
  (->> comments
       (flatten)
       (filter string?)))

(def negative-video-comments
    (clean-comments (fetch-comments negative-video-id)))

(def positive-video-comments
  (clean-comments (fetch-comments positive-video-id)))

(comment
  (dump-sentences "resources/negative-sentiment.txt" negative-video-comments)
  (dump-sentences "resources/positive-sentiment.txt" positive-video-comments)
  )
