;;; sneps3_printer.clj
;;; A printer for SNePS 3 terms.
;;; Author: Daniel R. Schlegel
;;; Modified: 6/3/2012

;(in-ns 'sneps3)
(ns csneps.core.printer
  (:require [csneps.core.contexts :as ct]
            [csneps.core :as csneps]
            [csneps.core.caseframes :as cf])
  (:use [csneps.core :only (type-of)]
        [clojure.pprint :only (cl-format pprint)]
        [csneps.util]
        [clojure.java.io]))

(declare print-term)

(defn print-atom
  [term]
  ;(println "Atom.")
  (:name term))

(defn wft-string
  [term]
  (str (:name term)
      (if (= (csneps/semantic-type-of term) :Proposition)
        (if (ct/asserted? term (ct/currentContext)) "!" "?"))":"))

(defn args-str
  [args]
  (map #(print-term %) args))

(defn print-negation
  [args]
  (str (cons (if (= (count args) 1) 'not 'nor) (args-str args))))

(defn print-negationbyfailure
  [args]
  (str (cons (if (= (count args) 1) 'thnot 'thnor) (args-str args))))

(defn print-param2op
  [fcn min max args]
  (str (list fcn (list min max) (args-str args))))

(defn print-nary
  [fcn args]
  (str (cons fcn (args-str args))))

(defn print-molecular
  [cf cs]
  ;(println "Pattern: " pattern " Slots: " slots " cs " cs)
  (if (cf/hasOneArgumentSlot cf)
    (let [fsym (first (:print-pattern cf))]
      (if (and (seq? fsym)
                     (= 'quote (first fsym)))
              (cons (second fsym) (if (set? (first cs)) 
                                    (map #(print-term %) (first cs)) 
                                    (list (print-term (first cs)))))
              (cons (print-term (first cs)) (if (set? (second cs)) 
                                              (map #(print-term %) (second cs)) 
                                              (list (print-term (second cs)))))))
    (seq
      (for [p (:print-pattern cf)]
        (cond
          (and (seq? p) (= (first p) 'quote))
          (second p)
          (symbol? p)
          (print-term (nth cs (first (positions #(= % p) (map #(:name %) (:slots cf))))))
          :else 
          (error "Bad pattern part "p" in the pattern "(:print-pattern cf)"."))))))


(defn print-unnamed-variable-term
  [term]
  ;(println "Printing var")
  (str
    (condp = (type-of term)
      :csneps.core/Arbitrary (str "(every " (:var-label term) " ")
      :csneps.core/Indefinite (str "(some " (:var-label term) "(" @(:dependencies-printable term) ") ")
      :csneps.core/QueryVariable (str "(" (:var-label term) " "))
    @(:restriction-set-printable term) ")"))

(defn print-unnamed-molecular-term
  [term]
  ;(println "Printing term: " term)
  (condp = (type-of term)
    :csneps.core/Negation
      (print-negation (first (:down-cableset term)))
    :csneps.core/Negationbyfailure
      (print-negationbyfailure (first (:down-cableset term)))
    :csneps.core/Conjunction
      (print-nary 'and (first (:down-cableset term)))
    :csneps.core/Disjunction
      (print-nary 'or (first (:down-cableset term)))
    :csneps.core/Equivalence
      (print-nary 'iff (first (:down-cableset term)))
    :csneps.core/Xor
      (print-nary 'xor (first (:down-cableset term)))
    :csneps.core/Nand
      (print-nary 'nand (first (:down-cableset term)))
    :csneps.core/Andor
      (print-param2op 'andor (:min term) (:max term) (first (:down-cableset term)))
    :csneps.core/Thresh
      (print-param2op 'thresh (:min term) (:max term) (first (:down-cableset term)))
    :csneps.core/Implication
      (str (list 'if (print-term (first (:down-cableset term))) (print-term (second (:down-cableset term)))))
    :csneps.core/Numericalentailment
      (str (list (symbol (str "=" (if (= (:min term) 1) :v (:min term)) ">"))
                 (first (:down-cableset term)) (second (:down-cableset term))))
    :csneps.core/Arbitrary
      (print-unnamed-variable-term term)
    :csneps.core/Indefinite
      (print-unnamed-variable-term term)
    :csneps.core/QueryVariable
      (print-unnamed-variable-term term)
    (print-molecular (:caseframe term) (:down-cableset term))
    ))

(defn sneps-printer
  [x]
  (condp = (type x)
    csneps.core.Atom (print-atom x *out*)
    csneps.core.Categorization (print-molecular x *out*)
    (pprint x *out*)))



(defn print-named-molecular-term
  "Prints to the stream
        the molecular term preceded by its wft name
        and an indication of its assert status."
  [term stream]
  (cl-format stream "~@<~W~:[~*~;~:[?~;!~]~]: ~W~:>"
	  (:name term)
	  (= (csneps/semantic-type-of term) :Proposition)
	  (ct/asserted? term (ct/currentContext)) term))

(defn print-named-variable-term
  [term]
  (str (:name term) ": " (print-unnamed-variable-term term)))


;(defmethod print-method csneps.core.Categorization [o w]
;  (print-named-molecular-term o w))

(defn print-slot
  [slotset]
  (if (= (count slotset) 1)
    (print-term (first slotset))
    (str "(setof " (apply str (interpose " " (for [n slotset] (print-term n)))) ")")))

(defn print-term
  [term]
  ;(println "Term: " term)
  (condp = (type-of term)
    clojure.lang.PersistentHashSet
      (print-slot term)
    :csneps.core/Atom
      (print-atom term)
    (print-unnamed-molecular-term term)))

(defn term-printer
  [term]
  (cond
    (isa? (csneps.core/syntactic-type-of term) :csneps.core/Variable)
      (str (print-named-variable-term term))
    (isa? (csneps.core/syntactic-type-of term) :csneps.core/Molecular)
      (str (wft-string term) (print-term term))
    :else
      (str (print-atom term)
        (if
          (and (= (csneps.core/semantic-type-of term) :Proposition)
	       (ct/asserted? term (ct/currentContext)))
          "!" ""))))

;(defn term-printer
;  [term]
;  (if (isa? (sneps3/syntactic-type-of term) :csneps.core/Molecular)
;    (do
;      (println (wft-string term) (print-term term)))
;    (do
;      (println (print-atom term)
;        (if
;          (and (= (sneps3/semantic-type-of term) :Proposition)
;	       (ct/asserted? term (ct/current-context)))
;          "!" "")))))

(defn sneps-printer
  [object]
  (if (isa? (csneps.core/syntactic-type-of object) :csneps.core/Term)
    (pprint (term-printer object))
    (pprint object)))

(defn sneps-printer-str
  [object]
  (if (isa? (csneps.core/syntactic-type-of object) :csneps.core/Term)
    (with-out-str (pprint (term-printer object)))
    (with-out-str (pprint object))))

(defn sexpr-printer
  [object]
  (cond
    (seq? object)
    (for [o object]
      (if (:name o)
        (:name o)
        (sexpr-printer o)))
    (set? object)
    (set (for [o object]
      (if (:name o)
        (:name o)
        (sexpr-printer o))))
    :else object))

(defn unif-printer
  [ulist]
  ;(println ulist)
  (for [m ulist]
    (into {} (map
      (fn [[k v]]
        [
         (if (isa? (csneps.core/syntactic-type-of k) :csneps.core/Variable)
           (print-atom k)
           (sexpr-printer k))
         (if (isa? (csneps.core/syntactic-type-of v) :csneps.core/Variable)
           (print-atom v)
           (sexpr-printer v))
         ]) m))))

;;; Print-method functions
(defmethod print-method csneps.core.Term [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Atom [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Base [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Variable [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Arbitrary [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Indefinite [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.QueryVariable [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Molecular [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Param2op [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Andor [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Disjunction [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Xor [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Nand [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Thresh [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Equivalence [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Conjunction [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Negation [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Negationbyfailure [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Numericalentailment [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Orentailment [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Implication [o w]
  (.write ^java.io.Writer w (str (term-printer o))))

(defmethod print-method csneps.core.Categorization [o w]
  (.write ^java.io.Writer w (str (term-printer o))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Writing CSNePS KBs to Files ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn writeKBToTextFile 
  "Writes the KB to the given text file,
   so that when that file is loaded,
   all the propositions asserted in the current KB
   will be asserted in the new KB.
   If the headerfile is included,
      a load of that file will be written before any of the asserts."
  [file & headerfile]
  (with-open [w ^java.io.Writer (writer file)]
    (.write w ";;; CSNePS KB\n")
    (.write w ";;; =========\n")
    (.write w (str ";;; " (.toString (new java.util.Date)) "\n"))
    (when headerfile 
      (.write ^java.io.Writer w "(clojure.lang.Compiler/loadFile " (first headerfile) ")\n"))
    (.write w ";;; Assumes that all required Contexts, Types, Slots, and Caseframes have now been loaded.\n(in-ns 'snuser)\n")
    (doall (map 
      #(do
         (doseq [hyp @(:hyps %)]
           (.write w  "(csneps.core.build/assert '")
           (if (= (:type hyp) :csneps.core/Atom)
             (.write w (str (print-atom hyp)))
             (.write w (str (print-unnamed-molecular-term hyp))))
           (.write w (str " '" (:name %) " :hyp)\n")))
         (doseq [der @(:ders %)]
           (.write w "(csneps.core.build/assert '")
           (if (= (:type der) :csneps.core/Atom)
             (.write w (str (print-atom der)))
             (.write w (str (print-unnamed-molecular-term der))))
           (.write w (str" '" (:name %) " :der)\n"))))
      (vals @ct/CONTEXTS)))))