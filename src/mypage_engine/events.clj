(ns mypage-engine.events
  (:require [mypage-engine.websocket :refer
             [get-channel
              ]]
            ))

(defn handle-event!
  [{:keys [name data]}]
  (println "event " name " data " data)
  (condp = name
    :get-channel (get-channel data)
    )
  )
