(ns ^:mb/once metabase.sample-data-test
  "Tests to make sure the Sample Database syncs the way we would expect."
  (:require
   [clojure.core.memoize :as memoize]
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.models :refer [Database Field Table]]
   [metabase.plugins :as plugins]
   [metabase.sample-data :as sample-data]
   [metabase.sync :as sync]
   [metabase.test :as mt]
   [metabase.util :as u]
   [metabase.util.files :as u.files]
   [toucan.hydrate :refer [hydrate]]
   [toucan2.core :as t2]))

;;; ---------------------------------------------------- Tooling -----------------------------------------------------

;; These tools are pretty sophisticated for the amount of tests we have!

(defn- sample-database-db []
  {:details (#'sample-data/try-to-extract-sample-database!)
   :engine  :h2
   :name    "Sample Database"})

(defmacro ^:private with-temp-sample-database-db
  "Execute `body` with a temporary Sample Database DB bound to `db-binding`."
  {:style/indent 1}
  [[db-binding] & body]
  `(mt/with-temp Database [db# (sample-database-db)]
     (sync/sync-database! db#)
     (let [~db-binding db#]
       ~@body)))

(defn- table
  "Get the Table in a `db` with `table-name`."
  [db table-name]
  (t2/select-one Table :name table-name, :db_id (u/the-id db)))

(defn- field
  "Get the Field in a `db` with `table-name` and `field-name.`"
  [db table-name field-name]
  (t2/select-one Field :name field-name, :table_id (u/the-id (table db table-name))))


;;; ----------------------------------------------------- Tests ------------------------------------------------------

(def ^:private extracted-db-path-regex #"^file:.*plugins/sample-database.db;USER=GUEST;PASSWORD=guest$")

(deftest extract-sample-database-test
  (testing "The Sample Database is copied out of the JAR into the plugins directory before the DB details are saved."
    (with-redefs [sync/sync-database! (constantly nil)]
      (with-temp-sample-database-db [db]
        (let [db-path (get-in db [:details :db])]
          (is (re-matches extracted-db-path-regex db-path))))))

  (testing "If the plugins directory is not creatable or writable, we fall back to reading from the DB in the JAR"
    (memoize/memo-clear! @#'plugins/plugins-dir*)
    (let [original-var u.files/create-dir-if-not-exists!]
      (with-redefs [u.files/create-dir-if-not-exists! (fn [_] (throw (Exception.)))]
        (with-temp-sample-database-db [db]
          (let [db-path (get-in db [:details :db])]
            (is (not (str/includes? db-path "plugins"))))

          (testing "If the plugins directory is writable on a subsequent startup, the sample DB is copied"
            (with-redefs [u.files/create-dir-if-not-exists! original-var]
              (memoize/memo-clear! @#'plugins/plugins-dir*)
              (sample-data/update-sample-database-if-needed! db)
              (let [db-path (get-in (t2/select-one Database :id (:id db)) [:details :db])]
                (is (re-matches extracted-db-path-regex db-path)))))))))

  (memoize/memo-clear! @#'plugins/plugins-dir*))

(deftest sync-sample-database-test
  (testing (str "Make sure the Sample Database is getting synced correctly. For example PEOPLE.NAME should be "
                "has_field_values = search instead of `list`.")
    (with-temp-sample-database-db [db]
      (is (= {:description      "The name of the user who owns an account"
              :database_type    "CHARACTER VARYING"
              :semantic_type    :type/Name
              :name             "NAME"
              :has_field_values :search
              :active           true
              :visibility_type  :normal
              :preview_display  true
              :display_name     "Name"
              :fingerprint      {:global {:distinct-count 2499
                                          :nil%           0.0}
                                 :type   {:type/Text {:percent-json   0.0
                                                      :percent-url    0.0
                                                      :percent-email  0.0
                                                      :percent-state  0.0
                                                      :average-length 13.532}}}
              :base_type        :type/Text}
             (-> (field db "PEOPLE" "NAME")
                 ;; it should be `nil` after sync but get set to `search` by the auto-inference. We only set `list` in
                 ;; sync and setting anything else is reserved for admins, however we fill in what we think should be
                 ;; the appropiate value with the hydration fn
                 (hydrate :has_field_values)
                 (select-keys [:name :description :database_type :semantic_type :has_field_values :active :visibility_type
                               :preview_display :display_name :fingerprint :base_type])))))))

(deftest write-rows-sample-database-test
  (testing "should be able to execute INSERT, UPDATE, and DELETE statements on the Sample Database"
    (with-temp-sample-database-db [db]
      (mt/with-db db
        (let [conn-spec (sql-jdbc.conn/db->pooled-connection-spec (mt/db))]
          (testing "update row"
            (let [quantity (fn []
                             (->> (jdbc/query conn-spec "SELECT QUANTITY FROM ORDERS WHERE ID = 1;")
                                  (map :quantity)))]
              (testing "before"
                (is (= [2]
                       (quantity))))
              (is (= [1]
                     (jdbc/execute! conn-spec "UPDATE ORDERS SET QUANTITY = 1 WHERE ID = 1;")))
              (testing "after"
                (is (= [1]
                       (quantity))))
              ;; TODO: this shouldn't be necessary, since we're modifying a temp sample database.
              (testing "restore"
                (is (= [1]
                       (jdbc/execute! conn-spec "UPDATE ORDERS SET QUANTITY = 2 WHERE ID = 1;"))))))
          (let [rating (fn []
                         (->> (jdbc/query conn-spec "SELECT RATING FROM PRODUCTS WHERE PRICE = 12.345;")
                              (map :rating)))]
            (testing "before"
              (is (= []
                     (rating))))
            (testing "insert row"
              (is (= [1]
                     (jdbc/execute! conn-spec "INSERT INTO PRODUCTS (price, rating) VALUES (12.345, 6.789);")))
              (is (= [6.789]
                     (rating))))
            (testing "delete row"
              (testing "before"
                (is (= [6.789]
                       (rating))))
              (is (= [1]
                     (jdbc/execute! conn-spec "DELETE FROM PRODUCTS WHERE PRICE = 12.345;")))
              (testing "after"
                (is (= []
                       (rating)))))))))))
