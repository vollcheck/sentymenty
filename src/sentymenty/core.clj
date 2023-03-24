(ns sentymenty.core
  (:require
   [clojure.string :as str]
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python :as py :refer [py. py.. py.-]]
   [kixi.stats.core :refer [mean]]))

(require-python '[spacy :as spacy]
                '[spacytextblob.spacytextblob.SpacyTextBlob :as SpacyTextBlob])

(def nlp
  (let [nlp (spacy/load "en_core_web_sm")]
    (py. nlp add_pipe "spacytextblob")
    nlp))

(defn text->polarity [nlp' text]
  (py.. (nlp' text)
        -_ -blob -polarity)) ;; like nlp._.blob.polarity in Python

(defn load-and-compute [nlp' path]
  (->> (slurp path)
       str/split-lines
       (map (partial text->polarity nlp'))))

(defn mean [coll]
  (/ (reduce + 0 coll) (count coll)))

(comment
  "resources/positive-sentiment.txt"
  "resources/negative-sentiment.txt"

  (def pos-coll (load-and-compute nlp
                                  "resources/positive-sentiment.txt"))

  (def neg-coll (load-and-compute nlp
                                  "resources/negative-sentiment.txt"))

  (transduce identity mean pos-coll)
   ;; => 0.14902176846455445

  (transduce identity mean neg-coll)
   ;; => -0.013954982722558225
  )
