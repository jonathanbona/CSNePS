;;; snip/unification.cl
;;; SNePS 3 Unification Algorithm
;;; Daniel R. Schlegel
;;; Standard unification based on a cross between that from clojure.core.unify, and
;;; Dr. Stuart C. Shapiro's CSE563 notes.
;;; Modified: 6/19/2012

(in-ns 'csneps.core.build)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Unification Algorithm ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare garner-unifiers unifySets term-predicate)

(defn varinlist? [list] #(some #{%} list))

(defn- composite?
  "Taken from the old `contrib.core/seqable?`. Since the meaning of 'seqable' is
   questionable, I will work on phasing it out and using a more meaningful
   predicate.  At the moment, the only meaning of `composite?` is:
   Returns true if `(seq x)` will succeed, false otherwise."
  [x]
  (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (nil? x)
      (instance? Iterable x)
      (-> x class .isArray)
      (string? x)
      (instance? java.util.Map x)))

(defn occurs?
  "Basic occurs check"
  [var term]
    (some #{var} (flatten term)))

(defn- bind-phase
  [binds variable expr index]
  (if (ignore-variable? variable)
    binds
    (if (zero? index)
      [(assoc (binds 0) variable expr) (binds 1)]
      [(binds 0) (assoc (binds 1) variable expr)])))

(defn- determine-occursness [want-occurs? variable? v expr binds]
  (if want-occurs?
    `(if (occurs? ~v ~expr)
       nil
       (bind-phase ~binds ~v ~expr))
    `(bind-phase ~binds ~v ~expr)))

;;; Set version.
;; Index is either 0 or 1 - 0 for target, 1 for source.
(defn- unify-variable-1binds
  [varp v expr binds index]
  (if-let [vb# ((binds index) v)]
    (garner-unifiers varp vb# expr binds)
    (if-let [vexpr# (and (varp expr) ((binds index) expr))]
      (garner-unifiers varp v vexpr# binds)
      (when-not (occurs? v expr)
        (bind-phase binds v expr index)))))

(defn- unify-variable
  [varp v expr binds index]
  ;(println "v: " v "expr: " expr "binds: " binds "index: " index)
  (if (seq? binds)
    (remove nil?
      (for [b binds]
        (unify-variable-1binds varp v expr b index)))
    (unify-variable-1binds varp v expr binds index)))

(defn- garner-unifiers
  "Attempt to unify x and y with the given bindings (if any). Potentially returns a map of the
   unifiers (bindings) found.  Will throw an `IllegalStateException` if the expressions
   contain a cycle relationship.  Will also throw an `IllegalArgumentException` if the
   sub-expressions clash."
  ([s t]           (garner-unifiers variable? s t))
  ([variable? s t] (garner-unifiers variable? s t [{} {}]))
  ([variable? s t binds] (garner-unifiers unify-variable variable? s t binds))
  ([uv-fn variable? s t [source target]]
    ;(println "Source:" s "Source Bindings:" source "\nTarget:" t "Target Bindings:" target)
     (cond
       (not (and source target)) [nil nil]
       (= s t)                   [source target]
       (set? s)                  (unifySets s (if (set? t) t (hash-set t)) [source target] 0)
       (set? t)                  (unifySets t (if (set? s) s (hash-set s)) [source target] 1)
       (variable? s)             (uv-fn variable? s t [source target] 0)
       (variable? t)             (uv-fn variable? t s [source target] 1)
       (every? seq? [s t]) (garner-unifiers variable?
                                                 (rest s)
                                                 (rest t)
                                                 (garner-unifiers variable?
                                                                  (first s)
                                                                  (first t)
                                                                  [source target]))
       (every? #(isa? (syntactic-type-of %) :csneps.core/Molecular) [s t])
                                 (garner-unifiers variable?
                                                 (:down-cableset s)
                                                 (:down-cableset t)
                                                 (garner-unifiers variable?
                                                                  (term-predicate s)
                                                                  (term-predicate t)
                                                                  [source target]))
       :else nil)))

(defn- subst-bindings-1bind
  "Flattens recursive bindings in the given map."
  [variable? [source target]]
  (let [substfn (fn [[k v]]
                   [k (loop [v v]
                        (if (and (variable? v) (or (source v) (target v)))
                          (recur (or (source v) (target v)))
                          v))])
        s (into {} (map substfn source))
        t (into {} (map substfn target))]
    [s t]))

(defn- subst-bindings
  ([binds] (subst-bindings variable? binds))
  ([variable? binds]
    (if (seq? binds)
      (for [b binds]
        (subst-bindings-1bind variable? b))
      (subst-bindings-1bind variable? binds))))

(defn- try-subst-map-1bind
  "Attempts to substitute the bindings in the appropriate locations in the given source/target bindings maps."
  [variable? [source target]]
  (let [substfn (fn [set1 set2]
                  (zipmap (keys set1)
                    (for [[k x] set1]
                      (term-prewalk (fn [expr] ;;prev was walk/prewalk
                                      (if (variable? expr)
                                        (or (set1 expr) (set2 expr) expr)
                                        expr))
                        x))))]
    [(substfn source target) (substfn target source)]))

(defn- try-subst-map
  [variable? binds]
  (if (seq? binds)
    (for [b binds]
      (try-subst-map-1bind variable? b))
    (try-subst-map-1bind variable? binds)))

(defn unify
  "Attemps the unification process from garnering the bindings to producing a
    final set of substitutions."
  ([x y] (unify variable? x y))
  ([variable? x y]
     (unify variable? x y (garner-unifiers variable? x y)))
  ([variable? x y binds]
;    (println "Unifying: " x " and " y)
    (if binds
      (->> binds
           (subst-bindings variable?)
           (try-subst-map variable?))
      nil)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Additions for Unification of Sets ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Permute subset takes as input a desired length of the permutations, and a set.
(defn permute-subset [n xs]
  (if (= 1 n)
    (map list xs)
    (reduce
      #(let [s (split-at (inc %2) xs)
             ps (permute-subset (dec n) (last s))
             a (last (first s))]
         (concat %1 (map (fn [p] (cons a p)) ps)))
      '() (range 0 (inc (- (count xs) n))))))

(defn findSetUnifiers [set1 set2]
  (vec (for [x set1]
          (vec (for [y set2]
                 (garner-unifiers x y))))))

(defn reduction [binds permutation]
  (reduce
    #(unify-variable variable? (first (first %2)) (second (first %2)) %1)
    binds
    permutation))

(defn extract-permutation [rows perm]
  "Takes a list of rows, and a permutation value, and returns the proper items
    from the rows. For example:
    (extract-permutation '([{?x ?z} {?x Alex}] [{?z (caregiver ?y)} nil]) [1 0])
    ({?x Alex} {?z (caregiver ?y)})"
  (for [p (map #(vector %1 %2) perm rows)]
    (nth (second p) (first p))))

;;; The Set-Based Unification Process
;;; Illustrated with the example: #{'?x '?y '(caregiver ?y)} (source) unified with #{'?z 'Alex} (target)
;;; 1) Find the unifiers for each term in the target set unified with each term
;;;    in the source set. This is done in findSetUnifiers.
;;;    Result:
;;;    [[{?y ?z} {?y Alex}]
;;;     [{?x ?z} {?x Alex}]
;;;     [{?z (caregiver ?y)} nil]]
;;; 2) Find permutations of size (count target) of the result of set 1. That is,
;;;    we're selecting every permutation of rows in the above table. This is
;;;    done using permute-subset.
;;;    Result:
;;;    (([{?y ?z} {?y Alex}] [{?x ?z} {?x Alex}])
;;;     ([{?y ?z} {?y Alex}] [{?z (caregiver ?y)} nil])
;;;     ([{?x ?z} {?x Alex}] [{?z (caregiver ?y)} nil]))
;;; 3) Now we have (count source) different row permutations. For each of these, we
;;;    need to find (count target) available combinations of substitutions.
;;;    We generate the available permutations by calling extract-permutations.
;;;    Result:
;;;    (({?y ?z} {?x Alex})
;;;     ({?y Alex} {?x ?z})
;;;     ({?y ?z} nil)
;;;     ({?y Alex} {?z (caregiver ?y)})
;;;     ({?x ?z} nil)
;;;     ({?x Alex} {?z (caregiver ?y)}))
;;; 4) We can safely ignore any of the above which have a "nil" list entry.
;;;    For the ones which remain, we combine the unifiers for the variables,
;;;    and return the valid results.
;;;    Result:
;;;    ({?x Alex, ?y ?z}
;;;     {?x ?z, ?y Alex}
;;;     {?z (caregiver ?y), ?y Alex}
;;;     {?z (caregiver ?y), ?x Alex})
;;; Note that the resolution of variables in these is handled later.

(defn unifySets [set1 set2 binds idx & {:keys [varfn] :or {varfn variable?}}]
  ;(println "Unifying sets: " set1 "\nand: " set2) 
  (let [unifiers (findSetUnifiers set1 set2) ;; Step 1.
        unifpermute (permute-subset (count set2) unifiers) ;; Step 2.
        reduction (fn [permutation]
          (reduce
            #(if (= idx 0)
               (unify-variable varfn (first (first (first %2))) (second (first (first %2))) %1 idx)
               (unify-variable varfn (second (first (first %2))) (first (first (first %2))) %1 idx))
            binds
            permutation))]
    ;(println "Set Unifiers " (first (first unifiers)))
    ;(println "Permuted subset unifiers: \n" unifpermute)
    (remove nil? 
      (flatten
        (for [p unifpermute] ;; Begin step 3
          (do
            ;(println "Permutation: " p)
            (for [sub (map #(extract-permutation p %) (cb/permutations (range (count p))))]
              ;(let [subs (map #(extract-permutation p2 %) (cb/permutations (range (count perms))))]
              (do
                ;(println "EXTRACTED: " sub)
                (when-not (some nil? sub)
                  ;(println "Calling reduction with substitutions: " sub)
                  (reduction sub)))))))))) ;Step 4


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Unification Tree Structure ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Map containing the distribution nodes for the tree.
(def DistNodes (ref {}))

;; A distribution node is made up of the predicate name and arity which it
;; representes, and the children in the tree.
(defrecord2 DistNode
  [name ""
   arity 0
   children (ref {})])

(defrecord2 UnifNode
  [acceptString ""      ;; The string for the item to be matched to.
   acceptWft nil
   acceptArity nil      ;; The arity of the item matching to.
   acceptDepth 0        ;; Depth of the current node in the expression.
   children (ref {})
   parent nil])         ;; Important for re-building sub-sexpressions...

;; TODO: Remove this once all references are gone.
;(defrecord2 TerminalNode
;  [sexpr ""
;   wft nil
;   parent nil])

;;;;;;;;;;;;;;;;;;;;;;;
;; Building the tree ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn findDistNode
  "Determines if a distribution node for the given name and arity already exist.
    If such a node exists, returns it, otherwise returns nil."
  [name arity distnodes]
  (get distnodes (str name arity)))

(defn findUnifNode
  "Determines if a unification node for term name of a given arity arleady exist
    as a child as the parent node. If so, returns it, otherwise returns nil."
  [name arity parent]
  (get @(:children parent) (str name arity)))

(defn buildUnificationTreeNode
  "Builds a unification tree node. If a parent node exists, the node is added
    as a child of the parent (if it isn't already). If there is no parent, a new
    distribution node is created (if one doesn't already exist) and returned."
  [termid arity & {:keys [parent wft distnodes] :or {distnodes DistNodes}}]
  (dosync
    (if parent
      (let [node (or (findUnifNode termid arity parent)
                     (new-unif-node {:acceptString termid :acceptArity arity :acceptWft wft :parent parent}))]         (alter (:children parent) assoc (str termid arity) node)
        node)
      (let [node (or (findDistNode termid arity @distnodes)
                     (new-dist-node {:name termid :arity arity}))]
        (alter distnodes assoc (str termid arity) node)
        node))))

(defn addTermToUnificationTree
  [term & {:keys [parent distnodes] :or {distnodes DistNodes}}]
  (if (isa? (syntactic-type-of term) :csneps.core/Molecular)
    (let [termid (term-predicate term)
          termpp (:print-pattern (:caseframe term))
          nofsyms (and (seq? (first termpp)) (= (first (first termpp)) 'quote))
          arity (- (count (:print-pattern (:caseframe term))) 1)
          newparent (buildUnificationTreeNode termid arity :parent parent :distnodes distnodes)] ; ;Builds the node for the predicate symbol
      (loop [p newparent
             dcs (if nofsyms
                   (:down-cableset term)
                   (rest (:down-cableset term)))]
        ;(println p "\n" dcs)
          (if (= dcs '())
            (buildUnificationTreeNode termid arity :parent p :wft term :distnodes distnodes) ;; Builds the node for the wft
            (recur (addTermToUnificationTree
                     (if (= 1 (count (first dcs))) ;;Singleton sets shouldn't be added as sets.
                       (first (first dcs)) 
                       (first dcs))
                     :parent p)
                   (rest dcs)))))
    (let [termid (or (:name term) (str term)) ;;Handling sets.
          arity 0
          newparent (buildUnificationTreeNode termid arity :parent parent :wft term :distnodes distnodes)]
      newparent)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tree Unification ;;;
;;;;;;;;;;;;;;;;;;;;;;;;

(declare unifyTreeWithChain)

;(defn molecular? [term]
;  (isa? (csneps/syntactic-type-of term) :csneps.core/Molecular))

(defn distnode? [node]
  (= (type node) csneps.core.build.DistNode))

(defn atomwftnode? [node]
  (= (:acceptArity node) 0))

(defn molwftnode? [node]
  (molecularTerm? (:acceptWft node)))

(defn labelnode? [node]
  (= (:acceptWft node) nil))

(defn- collectWftNodes
  [currnode & {:keys [depth] :or {depth 0}}]
  ;(println "Collecting! " depth (:acceptString currnode) currnode)
  (cond
    (and (= depth 1) (molwftnode? currnode))  currnode
    (molwftnode? currnode)                    (map #(collectWftNodes % :depth (dec depth)) (vals @(:children currnode)))
    (labelnode? currnode)                     (map #(collectWftNodes % :depth (inc depth)) (vals @(:children currnode)))
    :else                                     (map #(collectWftNodes % :depth depth) (vals @(:children currnode)))))

(defn- getWftNodesFor
  [currnode]
  ;(println "----")
  (if (atomwftnode? currnode)
    (list currnode)
    (flatten (collectWftNodes currnode))))

(defn- treeUnifyVar
  [variable? sourcenode targetnode [s t]]
  ;(println "Unifying: " (garner-unifiers variable? (:acceptWft sourcenode) (:acceptWft targetnode) [s t]))
  [sourcenode targetnode (garner-unifiers variable? (:acceptWft sourcenode) (:acceptWft targetnode) [s t])])

(defn- treeUnifyVarList
  "Takes a variable wft and a list of nodes representing wfts to unify the variable
   with. It also takes a source and target binding, along with index representing
   whether or not the result goes in the source or target. 0 = variable is source, 1 = x is source "
  [variable? sourcenodelist targetnode [s t]]
  ;(println "Unify var list..." (for [sn sourcenodelist] (treeUnifyVar variable? sn targetnode [s t])))
  (let [unifiers (for [sn sourcenodelist] (treeUnifyVar variable? sn targetnode [s t]))]
    (doall 
      (for [[sn tn [sb tb]] unifiers] 
        (map #(unifyTreeWithChain @(:children tn) :variable? variable? :s sb :t tb :source %) (vals @(:children sn)))))))

(defn unifyVarTree
  [variable? sourcenode targetnode [s t]]
  (treeUnifyVarList variable? (getWftNodesFor sourcenode) (first (getWftNodesFor targetnode)) [s t]))

(defn unifyTreeWithChain
  [target & {:keys [variable? s t source] :or {variable? variable? s {} t {} source true}}]
  (let [targetnode (second (first target))
        sourcenode (if (= (type targetnode) csneps.core.build.DistNode)
                     (findDistNode (:name targetnode) (:arity targetnode) @DistNodes)
                     source)]
    ;(println "Source: " sourcenode "\nTarget: " targetnode)
    ;(println "Source Binding:" s "\nTarget Binding:" t)
    (cond
      (not (and s t))                                 [nil nil]
      (and (nil? sourcenode) (nil? targetnode))       [s t]
      (distnode? sourcenode)                          (map #(unifyTreeWithChain
                                                              @(:children targetnode)
                                                              :variable? variable? :s s :t t :source %)
                                                        (vals @(:children sourcenode)))
      (= (:acceptWft sourcenode)
         (:acceptWft targetnode))                     (map #(unifyTreeWithChain
                                                              @(:children targetnode)
                                                              :variable? variable? :s s :t t :source %)
                                                        (vals @(:children sourcenode)))
      (and (molecularTerm? (:acceptWft sourcenode))
           (molecularTerm? (:acceptWft targetnode))
           (or (not-empty @(:children sourcenode))
               (not-empty @(:children targetnode))))  (map #(unifyTreeWithChain
                                                              @(:children targetnode)
                                                              :variable? variable? :s s :t t :source %)
                                                        (vals @(:children sourcenode)))
      (and (molecularTerm? (:acceptWft sourcenode))
           (molecularTerm? (:acceptWft targetnode)))      {:source (:acceptWft sourcenode) 
                                                       :target (:acceptWft targetnode) 
                                                       :sourcebind s 
                                                       :targetbind t}
      (variable? (:acceptWft sourcenode))             (unifyVarTree variable? sourcenode targetnode [s t])
      (variable? (:acceptWft targetnode))             (unifyVarTree variable? sourcenode targetnode [s t])
      :else nil)))

(defn getUnifiers
  [term & {:keys [varfn] :or {varfn variable?}}]
  (let [tempdist (ref {})
        termchain (addTermToUnificationTree term :distnodes tempdist)
        unifiers (when (findDistNode (:name (second (first @tempdist))) (:arity (second (first @tempdist))) @DistNodes)
                   (remove nil? (flatten (unifyTreeWithChain @tempdist :variable? varfn))))]
    (println unifiers)
    (for [u unifiers
          :let [[subsourcebind subtargetbind] (->> [(:sourcebind u) (:targetbind u)]
                                                (subst-bindings variable?)
                                                (try-subst-map variable?))]]
      {:source (:source u) :target (:target u) :sourcebind subsourcebind :targetbind subtargetbind})))

      ;(println u))
      
    
    ;(if unifiers
    ;  (->> unifiers
    ;       (subst-bindings variable?)
    ;       (try-subst-map variable?))
    ;  nil)
    ;unifiers))

;(defn test []
;  (def t1 (snuser/assert '(Isa Glacier Cat)))
;  (def t2 (snuser/assert '(Isa Glacier Animal)))
;  (addTermToUnificationTree t1)
;  (addTermToUnificationTree t2)
;  (def t3 (snuser/assert '(Isa (every x) Animal)))
;  (getUnifiers t3))
  

;;;;;;;;;;;;;;;;;;;;;;;;
;;; Diagnostic Tools ;;;
;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-tree [node & {:keys [depth] :or {depth 0}}]
  (when node
    (let [currnode (if (map? node) (second (first node)) (second node))]
      (println (apply str (repeat depth "-")) (if (:acceptWft currnode)
                                                (csneps.core.printer/term-printer (:acceptWft currnode))
                                                (or (:name currnode) (:acceptString currnode))))
      (doseq [c @(:children currnode)]
        (print-tree c :depth (inc depth))))))

(defn print-all-trees []
  (doseq [d @DistNodes] (print-tree d))) 
