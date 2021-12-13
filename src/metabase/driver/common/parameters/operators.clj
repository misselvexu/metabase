(ns metabase.driver.common.parameters.operators
  "This namespace handles parameters that are operators.

    {:type :number/between
     :target [:dimension
              [:field
               26
               {:source-field 5}]]
     :value [3 5]}"
  (:require [metabase.mbql.schema :as mbql.s]
            [metabase.models.params :as params]
            [metabase.query-processor.error-type :as qp.error-type]
            [schema.core :as s]))

(s/defn ^:private operator-arity :- (s/maybe (s/enum :unary :binary :variadic))
  [param-type]
  (get-in mbql.s/parameter-types [param-type :operator]))

(defn operator?
  "Returns whether param-type is an \"operator\" type."
  [param-type]
  (boolean (operator-arity param-type)))

(s/defn ^:private verify-type-and-arity
  [field param-type param-value]
  (letfn [(maybe-arity-error [n]
            (when (not= n (count param-value))
              (throw (ex-info (format "Operations Invalid arity: expected %s but received %s"
                                      n (count param-value))
                              {:param-type  param-type
                               :param-value param-value
                               :field-id    (second field)
                               :type        qp.error-type/invalid-parameter}))))]
    (condp = (operator-arity param-type)
      :unary
      (maybe-arity-error 1)

      :binary
      (maybe-arity-error 2)

      :variadic
      (when-not (seq param-value)
        (throw (ex-info (format "No values provided for operator: %s" param-type)
                        {:param-type  param-type
                         :param-value param-value
                         :field-id    (second field)
                         :type        qp.error-type/invalid-parameter})))

      (throw (ex-info (format "Unrecognized operation: %s" param-type)
                      {:param-type  param-type
                       :param-value param-value
                       :field-id    (second field)
                       :type        qp.error-type/invalid-parameter})))))

(s/defn to-clause :- mbql.s/Filter
  "Convert an operator style parameter into an mbql clause. Will also do arity checks and throws an ex-info with
  `:type qp.error-type/invalid-parameter` if arity is incorrect."
  [{param-type :type [a b :as param-value] :value [_ field :as _target] :target :as param}]
  (verify-type-and-arity field param-type param-value)
  (let [field' (params/wrap-field-id-if-needed field)]
    (condp = (operator-arity param-type)
      :binary
      [(keyword (name param-type)) field' a b]

      :unary
      [(keyword (name param-type)) field' a]

      :variadic
      (into [(keyword (name param-type)) field'] param-value)

      (throw (ex-info (format "Unrecognized operator: %s" param-type)
                      {:param-type param-type
                       :param-value param-value
                       :field-id    (second field)
                       :type        qp.error-type/invalid-parameter})))))
