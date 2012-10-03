;;   Copyright (c) Metadata Partners, LLC. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns datomic.codeq.core
  (:require [datomic.api :as d]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import java.util.Date)
  (:gen-class))

(set! *warn-on-reflection* true)

(defn ^java.io.Reader exec-stream 
  [^String cmd]
  (-> (Runtime/getRuntime)
      (.exec cmd) 
      .getInputStream 
      io/reader))

(def schema
     [
      {:db/id #db/id[:db.part/db]
       :db/ident :git/commit
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one
       :db/doc "Associate tx with this git commit"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :codeq/op
       :db/valueType :db.type/keyword
       :db/index true
       :db/cardinality :db.cardinality/one
       :db/doc "Associate tx with this operation - one of :import, :analyze"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/type
       :db/valueType :db.type/keyword
       :db/cardinality :db.cardinality/one
       :db/index true
       :db/doc "Type enum for git objects - one of :commit, :tree, :blob, :tag"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/sha
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/doc "A git sha, should be in repo"
       :db/unique :db.unique/identity
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/parents
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many
       :db/doc "Parents of a commit"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/deletes
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many
       :db/doc "Git objects (trees/blobs) deleted by a commit"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/message
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/doc "A commit message"
       :db/fulltext true
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/author
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one
       :db/doc "Person who authored a commit"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/authoredAt
       :db/valueType :db.type/instant
       :db/cardinality :db.cardinality/one
       :db/doc "Timestamp of authorship of commit"
       :db/index true
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/committer
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one
       :db/doc "Person who committed a commit"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/committedAt
       :db/valueType :db.type/instant
       :db/cardinality :db.cardinality/one
       :db/doc "Timestamp of commit"
       :db/index true
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/nodes
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many
       :db/doc "Nodes of a git tree"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/path
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/doc "Path of a tree node"
       :db/index true
       :db/fulltext true
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/object
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one
       :db/doc "Git object (tree/blob) in a tree node"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :git/prior
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one
       :db/doc "Node containing prior value of a git object"
       :db.install/_attribute :db.part/db}

      {:db/id #db/id[:db.part/db]
       :db/ident :person/email
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/doc "An email address"
       :db/unique :db.unique/identity
       :db.install/_attribute :db.part/db}
      ])

(defn ensure-schema [conn]
  (or (-> conn d/db (d/entid :git/commit))
      @(d/transact conn schema)))

;;example commit - git cat-file -p
;;tree d81cd432f2050c84a3d742caa35ccb8298d51e9d
;;author Rich Hickey <richhickey@gmail.com> 1348842448 -0400
;;committer Rich Hickey <richhickey@gmail.com> 1348842448 -0400

;; or

;;tree ba63180c1d120b469b275aef5da479ab6c3e2afd
;;parent c3bd979cfe65da35253b25cb62aad4271430405c
;;maybe more parents
;;author Rich Hickey <richhickey@gmail.com> 1348869325 -0400
;;committer Rich Hickey <richhickey@gmail.com> 1348869325 -0400


;;example tree
;;100644 blob ee508f768d92ba23e66c4badedd46aa216963ee1	.gitignore
;;100644 blob b60ea231eb47eb98395237df17550dee9b38fb72	README.md
;;040000 tree bcfca612efa4ff65b3eb07f6889ebf73afb0e288	doc
;;100644 blob 813c07d8cd27226ddd146ddd1d27fdbde10071eb	epl-v10.html
;;100644 blob f8b5a769bcc74ee35b9a8becbbe49d4904ab8abe	project.clj
;;040000 tree 6b880666740300ac57361d5aee1a90488ba1305c	src
;;040000 tree 407924e4812c72c880b011b5a1e0b9cb4eb68cfa	test

;;example diff-tree
;;RichMacPro:datomic rich$ git diff-tree -r -c -M -C -t --no-commit-id --root 25a6763864c48bee17ce4872b040c9427b6dbec9
;;:040000 040000 c70bd51b94ca4db0a2aa655bc6ba68c422ee7de2 98c0c5199bbde068d2c41505370f8692b67d5d20 M	build
;;:100644 100644 1203148b1adc481d30d7509b9d2fb4124fe6ac1f e69fbb4496d1101d53560e84475e5ef13d484dfa M	build/packaging.org
;;:040000 040000 32f70caacd52279587b42a38227977eab503c16b 903c61af7f9acd99dc8262801ed18261ef1e185c M	siderail
;;:100644 100644 ce62de79aabd59d8b5994f0b8b5cb893cface5e3 70ae7b725c861b1401c26a6f51b5c4f5cb3264eb R053	src/clj/datomic/aws_runtime.clj	siderail/snippets.clj
;;:040000 040000 01707b6d46d7c8d19384b7948d3b385c60d8e1a3 2f66f5d7f95031b9b6d2077ebf1c8ac89ccc3a7d M	src
;;:040000 040000 1a7394d6083bf797dabaa9e21a239f72e32dddf8 81735f5ffa837e7e778eadc6189fdd3687818fee M	src/clj
;;:040000 040000 0a72bc83597a0294eb3eabc64755191c65f6af36 9dca65a1b1a3db07d6ea4bfdd7db0dd0acbcc03d M	src/clj/datomic
;;:000000 100644 0000000000000000000000000000000000000000 3cb285abbac17e3a9a983524b6d18a0658994070 A	src/clj/datomic/aws_detect.clj
;;:100644 100644 595a6ab95fdefda585be139beb73f1427e37e8cf 1cfcbb19f269a6b2d857b5c48cda98d476269714 M	src/clj/datomic/connector.clj
;;:100644 100644 041c22c6c8466aa2f1c5ef577ca661a1c6dc2169 43e633d190b4d8469f915394a1949c1c226bc894 M	src/clj/datomic/transactor.clj
;;:040000 040000 57b7f95c0791ef307dbc0edab5980dcd3c1e5577 1477ca0fbe83a0e3fcbbe06631e82170d2ffd467 M	test
;;:040000 040000 6d2a855f5bcfc6dbb7edec71562dc0b588d89ce7 69b10b84c358a37eed41dbe1628870f3d78c3b38 M	test/src
;;:040000 040000 f8749c54f2630b31476a5e8a53645ea9608a7ed8 f775a1741648a2b6ae7b588c16a642ee9dab8879 M	test/src/dtest
;;:100644 100644 9a57b8bc470a1e70f73f201bddda5b02cae14b76 037adb65eadc7c7f6a62324b1682524edabf9ca4 M	test/src/dtest/web.clj
;;:040000 040000 f1858d625337a7b43e3c99b1b6c1c1ed61a3f535 b7f7aa245f7de94f7716e8ed3e462d5865777246 M	tix
;;:100644 100644 f3def8b510500009953879f2d89d93f554e86e67 5f9b77505a97f690ff12b3c18b0b871b09725b85 M	tix/datomic-free.org

(defn dir
  "Returns [[sha path] ...]"
  [tree]
  (with-open [s (exec-stream (str "git cat-file -p " tree))]
    (let [es (line-seq s)]
      (mapv #(vector (nth (string/split ^String % #"\s") 2)
                     (subs % (inc (.indexOf ^String % "\t")) (count %))) es))))

(defn changes [tree]
  (with-open [s (exec-stream (str "git diff-tree -r -c -M -C -t --no-commit-id --root " tree))]
    (mapv
     (fn [^String e]
       (let [mode1 (subs e 1 7)
             mode2 (subs e 8 14)
             sha1 (subs e 15 55)
             sha2 (subs e 56 96)
             prior-sha (when (not= sha1 "0000000000000000000000000000000000000000") sha1)
             sha (when (not= sha2 "0000000000000000000000000000000000000000") sha2)
             op (subs e 97 98)
             path (subs e (inc (.lastIndexOf e "\t")) (count e))]
         (into {}
               (remove (fn [[_ v]] (nil? v))
                       {:prior-sha (when (not= op "T") prior-sha)
                        :sha sha
                        :op ({"A" :add "M" :modify "D" :delete "C" :copy "R" :rename "T" :type-change} op)
                        :path path
                        :prior-path (cond (= op "D") path
                                          (= op "R") (subs e (inc (.indexOf e "\t")) (.lastIndexOf e "\t")))
                        :dir (when (and sha (= mode2 "040000")) (dir sha))
                        }))))
     (line-seq s))))

(defn commit
  [[sha msg]]
  (let [trim-email (fn [s] (subs s 1 (dec (count s))))
        dt (fn [ds] (Date. (* 1000 (Integer/parseInt ds))))
        [tree parents author committer]
        (with-open [s (exec-stream (str "git cat-file -p " sha))]
          (let [lines (mapv #(string/split % #"\s") (line-seq s))
                tree (-> lines (nth 0) (nth 1))
                [plines xs] (split-with #(= (nth % 0) "parent") (rest lines))]
            [tree
             (seq (map second plines))
             (vec (reverse (first xs)))
             (vec (reverse (second xs)))]))]
    {:sha sha
     :msg msg
     :tree tree
     :dir (dir tree)
     :parents parents
     :changes (changes sha)
     :author (trim-email (author 2))
     :authored (dt (author 1))
     :committer (trim-email (committer 2))
     :committed (dt (committer 1))}))

(defn index-get-id
  [db attr v]
  (let [d (first (d/index-range db attr v))]
    (when (and d (= (:v d) v))
      (:e d)))
  #_(ffirst (d/q '[:find ?e :in $ ?a ?v :where [?e ?a ?v]]
                 db attr v)))

(defn index->id-fn
  [db attr]
  (memoize
   (fn [x]
     (or (index-get-id db attr x)
         (d/tempid :db.part/user)))))

(defn commit-tx-data
  [db commit]
  (let [tempid? map? ;;todo - better
        sha->id (index->id-fn db :git/sha)
        email->id (index->id-fn db :person/emailcz)
        ]))

(defn commits
  "Returns log as [[sha msg] ...], in commit order. commit-name may be nil
  or any acceptable commit name arg for git log"
  [commit-name]
  (let [commits (with-open [s (exec-stream (str "git log --pretty=oneline " commit-name))]
                  (reverse
                   (mapv
                    #(vector (subs % 0 40)
                             (subs % 41 (count %)))
                    (line-seq s))))] 
    commits))

(defn unimported-commits
  [db commit-name]
  (let [imported (into {}
                       (d/q '[:find ?sha ?e
                              :where
                              [?tx :codeq/op :import]
                              [?tx :git/commit ?e]
                              [?e :git/sha ?sha]]
                            db))]
    (pmap commit (remove (fn [[sha _]] (imported sha)) (commits commit-name)))))


(defn ensure-db [db-uri]
  (let [newdb? (d/create-database db-uri)
        conn (d/connect db-uri)]
    (ensure-schema conn)
    conn))

(defn -main
  [& [db-uri commit]]
  (if db-uri
    (let [conn (ensure-db db-uri)] 
      (unimported-commits (d/db conn) commit))
    (println "Usage: datomic.codeq.core db-uri [commit-name]")))


(comment
(datomic.codeq.core/-main "datomic:mem://test" "20f8db11804afc8c5a1752257d5fdfcc2d131d08")
)