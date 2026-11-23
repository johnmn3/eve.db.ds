;; flagship4 probe v2: bisect eve.atom's require cone on the glojure
;; seat (landed tree). Prints BEFORE each require.
(println :p0-completion) (flush)
(require 'eve.host-completion)
(println :p1-eve-host) (flush)
(require 'eve.host)
(println :p2-eve-encoding) (flush)
(require 'eve.encoding)
(println :p3-eve-int32) (flush)
(require 'eve.int32)
(println :p4-eve-process) (flush)
(require 'eve.process)
(println :p5-journal-file) (flush)
(require 'eve.journal-file)
(println :p6-read-region) (flush)
(require 'eve.read-region)
(println :p7-deftype-data) (flush)
(require 'eve.deftype-proto.data)
(println :p8-eve-atom) (flush)
;; kondo merges ns-less top-level scripts into one implicit user
;; ns, so this require reads as a duplicate of probe0_eve.clj's;
;; each script runs standalone and needs its own.
#_{:clj-kondo/ignore [:duplicate-require]}
(require 'eve.atom)
(println :probe-v2-complete true)
