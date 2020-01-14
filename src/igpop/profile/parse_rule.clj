(ns igpop.profile.parse-rule
  (:require
    [flatland.ordered.map :refer :all]
    [clojure.string :as s]
    [clojure.pprint :as p]
    ))

(def constraint-struct '(:requirements :severity :human :expression :xpath :source))

(defn fhirpath-rule
  [k coll]
  {k (mapv
       (fn [item]
         (let [k (first (keys item))
               v (first (vals item))]
           (reduce
             (fn [acc k]
               (into acc
                     (if (contains? v k)
                       {k (get v k)}
                       (cond
                         (= k :severity) {k "error"}
                         (= k :human) (if-let [str (get v :description)] {k str})))))
             (ordered-map {:key (name k)}) constraint-struct)))
       coll)})

(defn mustSupport
  ([] (mustSupport true))
  ([v] {:mustSupport v}))

(defn cardinality [k v] (cond
                          (= k :required) (if (= true v) {:min 1})
                          (= k :disabled) (if (= true v) {:max 0})
                          (= k :minItems) {:min v}
                          (= k :maxItems) {:max v}))

;;target url = https://healthsamurai.github.io/igpop/profiles/{resourceType}/basic.html
(defn refers [k v]
  {:type
   (reduce (fn [outer-acc ordmap]
             (conj outer-acc
                   (reduce (fn [acc [key val]]
                             (into acc (if (= key :resourceType)
                                         {:targetProfile [(str "https://healthsamurai.github.io/igpop/profiles/" val "/basic.html")]})))
                           (ordered-map {:code "Reference"}) ordmap)
                   )) [] v)})

(defn valueset
  [k v]
  (let [resource (last (s/split "fhir:administrative-gender" #":"))
        value-set-url (str "http://hl7.org/fhir/ValueSet/" resource)]
    {:binding {
               :strength "required",
               :valueSet value-set-url
               }}))

(defn description [k v])

(defn collection [k v])

(defn val-type [k v])

(defn poly [k v])

(defn extension [k {name :name id :id}]
  {:id          id,
   :path        id,
   :sliceName   name,
   :min         0,
   :max         "1",
   :type        [{:code    "Extension",
                  :profile [(str "http://hl7.org/fhir/us/core/StructureDefinition/" name)]}],
   :mustSupport true,
   :mapping     [{:identity "argonaut-dq-dstu2",
                  :map      id}]})


(defn base-extension [id path]
  {:id          id,
   :path        path,
   :sliceName   name,
   :min         0,
   :max         "1",
   :type        [{:code    "Extension",
                  :profile [(str "http://hl7.org/fhir/us/core/StructureDefinition/" name)]}],
   :mustSupport true,
   :mapping     [{:map id}]})

(defn url-extension [id path]
  {:id       "Extension.extension:ombCategory.url",
   :path     "Extension.extension.url",
   :min      1,
   :max      "1",
   :type     [{:code "uri"}],
   :fixedUri "ombCategory"})

(defn value-extension [id path type required description]
  {:id      (str path ".value" type),
   :path    (str path ".value" type),
   :min     1,
   :max     "1",
   :type    [{:code type}],
   :binding {:strength    required,
             :description description,
             :valueSet    "http://hl7.org/fhir/us/core/ValueSet/omb-race-category"}})

(def domain-resource-agenda {:required    cardinality
                             :disabled    cardinality
                             :minItems    cardinality
                             :maxItems    cardinality
                             :constraints fhirpath-rule
                             :mustSupport mustSupport
                             :refers      refers
                             :valueset    valueset
                             :description description
                             :collection  collection
                             :type        val-type
                             :union       poly
                             :value       poly
                             :extension   extension})

(defn general-type [profile-type]
  (let [mapping {:Patient   :DomainResource
                 :Extension :Extension}]
    (get mapping profile-type)))

(def default-agenda {:mustSupport mustSupport})


(defn extension-default-elements
  [id]
  [{:id       "Extension.url",
    :path     "Extension.url",
    :min      1,
    :max      "1",
    :fixedUri (str "http://hl7.org/fhir/us/core/StructureDefinition/" id)},
   {:id   "Extension.value[x]",
    :path "Extension.value[x]",
    :min  0,
    :max  "0"}])


(defn default-elements
  [type]
  (get {:DomainResource []
        :Extension      extension-default-elements} type))

(def snapshot-elements
  [:base
   :id
   :url
   :value
   :extension.base
   :extension.id
   :extension.url
   :extension.valueX
   :extension.valueX.value
   :extension.extension])

(def differential-elements
  [:base
   :url
   :value])

(defn ->str-path
  [vec-path]
  (clojure.string/join "." (map name vec-path)))

(defn parse-extension-diff
  [path {required :required description :description type :type :as props}]
  (let [key-name (name (second path))
        path (->str-path path)]
   (into []
         (concat (extension-default-elements key-name)
                 [(base-extension key-name path)
                  (url-extension key-name path)
                  (value-extension key-name path required type description)]))))

(defn parse-extension-snapshot
  [path {required :required description :description type :type :as props}]
  (let [key-name (name (second path))
        path (->str-path path)]
   (into []
         (concat (extension-default-elements key-name)
                 [(base-extension key-name path)
                  (url-extension key-name path)
                  (value-extension key-name path required type description)]))))