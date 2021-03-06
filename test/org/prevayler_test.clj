(ns org.prevayler-test
  (:require
    [org.prevayler :refer :all]
    [midje.sweet :refer :all])
  (:import
    [java.io File]))

; (do (require 'midje.repl) (midje.repl/autotest))

(defn- tmp-file []
  (doto
    (File/createTempFile "test-" ".tmp")
    (.delete)))

(facts "About prevalence"
  (let [handler-fn (fn [state event]
                     (when (= event "boom") (throw (RuntimeException.)))
                     [(str state event)
                      (str "+" event)])
        initial-state "A"
        file (tmp-file)

        prev! #(prevayler! handler-fn initial-state file)]

    (fact "First run uses initial state"
      (with-open [p (prev!)]
        @p => "A"))

    (fact "Restart after no events recovers initial state"
      (with-open [p (prev!)]
        @p => "A"
        (handle! p "B") => ["AB" "+B"]
        @p => "AB"
        (handle! p "C") => ["ABC" "+C"]
        @p => "ABC"
        (eval! p "D") => "+D"
        @p => "ABCD"
        (step! p "E") => "ABCDE"
        @p => "ABCDE"))

    (fact "Restart after some events recovers last state"
      (with-open [p (prev!)]
        @p => "ABCDE"))

    (fact "Simulated crash during restart is survived"
      (assert (.renameTo file (backup-file file)))
      (spit file "#$@%@corruption&@#$@")
      (with-open [p (prev!)]
        @p => "ABCDE"))

    (fact "Simulated crash during event handle will fall through"
      (with-open [p (prev!)]
        (handle! p "boom") => (throws RuntimeException)
        @p => "ABCDE"
        (step! p "F") => "ABCDEF"))

    (fact "Restart after some crash during event handle recovers last state"
          (with-open [p (prev!)]
            @p => "ABCDEF"))

    (fact "File is released after Prevayler is closed"
      (assert (.delete file)))))
