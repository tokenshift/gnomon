(ns gnomon
  "A wrapper around the Java WatchService."
  (:require [clojure.set :as set])
  (:import [java.nio.file FileSystems Paths StandardWatchEventKinds WatchEvent]))

(def ^:private event-kinds
  {:create StandardWatchEventKinds/ENTRY_CREATE
   :delete StandardWatchEventKinds/ENTRY_DELETE
   :modify StandardWatchEventKinds/ENTRY_MODIFY
   :overflow StandardWatchEventKinds/OVERFLOW})

(def ^:private event-kind-mapping (set/map-invert event-kinds))

(defn- path-to-file
  [reg-path event-path]
  (let [path (.resolve reg-path event-path)
        fname (str path)
        file (.toFile path)
        ftype (cond (.isDirectory file) :directory
                    (.isFile file) :file
                    :else :unknown)]
    {:type ftype
     :path fname}))

(defn- handle-events
  [watcher callback]
  (try
    (loop []
      (let [event-key (.take watcher)]
        (doseq [event (.pollEvents event-key)]
          (callback (event-kind-mapping (.kind event))
                    (path-to-file (.watchable event-key) (.context event))))
        (if (.reset event-key)
          (recur)
          (callback :error "Could not reset event registration; check folder permissions."))))
    (catch java.lang.IllegalMonitorStateException e)))

(defn watch
  [callback & paths]
  (let [filesystem (FileSystems/getDefault)
        watcher (.newWatchService filesystem)
        paths (map #(Paths/get % (into-array String [])) paths)]
    (doseq [path paths] (.register path watcher (into-array (vals event-kinds))))
    (future (handle-events watcher callback)))
  nil)
