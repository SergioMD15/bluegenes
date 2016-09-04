(ns redgenes.sections.saveddata.events
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx dispatch]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [imcljs.filters :as im-filters]
            [cljs-uuid-utils.core :as uuid]
            [cljs-time.core :as t]))

(defn get-parts
  [model query]
  (group-by :type
            (distinct
              (map (fn [path]
                     (assoc {}
                       :type (im-filters/im-type model path)
                       :path (str (im-filters/trim-path-to-class model path) ".id")))
                   (:select query)))))


(reg-event-db
  :saved-data/calculate-parts
  (fn [db]
    (let [model (get-in db [:assets :model])
          items (get-in db [:saved-data :items])])
    db))

(reg-event-fx
  :saved-data/toggle-edit-mode
  (fn [{db :db}]
    {:db       (update-in db [:saved-data :list-operations-enabled] not)
     :dispatch [:saved-data/calculate-parts]}))

(reg-event-fx
  :save-data
  (fn [{db :db} [_ data]]
    (let [new-id (str (uuid/make-random-uuid))
          model  (get-in db [:assets :model])]
      {:db       (assoc-in db [:saved-data :items new-id]
                           (-> data
                               (merge {:created (t/now)
                                       :updated (t/now)
                                       :parts   (get-parts model (:value data))})))
       :dispatch [:open-saved-data-tooltip
                  {:label (:label data)
                   :id    new-id}]})))

(reg-event-db
  :open-saved-data-tooltip
  (fn [db [_ data]]
    (assoc-in db [:tooltip :saved-data] data)))

(reg-event-db
  :save-saved-data-tooltip
  (fn [db [_ id label]]
    (-> db
        (assoc-in [:saved-data :items id :label] label)
        (assoc-in [:tooltip :saved-data] nil))))

(reg-event-db
  :saved-data/set-type-filter
  (fn [db [_ kw]]
    (let [unset? (empty? (mapcat (fn [[_ paths]]
                                   paths) (get-in db [:saved-data :editor :items])))]
      (if unset?
        (update-in db [:saved-data :editor] dissoc :filter)
        (assoc-in db [:saved-data :editor :filter] kw)))
    ))

(reg-event-db
  :saved-data/toggle-editable-item
  (fn [db [_ id path-info]]
    (let [exists-fn #(= path-info %)]
      (if (empty? (filter exists-fn (get-in db [:saved-data :editor :items id])))
        (update-in db [:saved-data :editor :items id] conj path-info)
        (let [removed (update-in db [:saved-data :editor :items id] (fn [items] (remove exists-fn items)))]
          (if (empty? (get-in removed [:saved-data :editor :items id]))
            (update-in removed [:saved-data :editor :items] dissoc id)
            removed))))))

;(reg-event-db
;  :saved-data/perform-operation
;  (fn [db]
;    (let [ids])))


