(ns nightcode.core
  (:require [seesaw.core :as s]
            [nightcode.dialogs :as dialogs]
            [nightcode.editors :as editors]
            [nightcode.lein :as lein]
            [nightcode.projects :as p]
            [nightcode.shortcuts :as shortcuts]
            [nightcode.ui :as ui]
            [nightcode.utils :as utils])
  (:import [java.awt.event WindowAdapter]
           [javax.swing.event TreeExpansionListener TreeSelectionListener]
           [javax.swing.tree TreeSelectionModel]
           [org.pushingpixels.substance.api SubstanceLookAndFeel]
           [org.pushingpixels.substance.api.skin GraphiteSkin])
  (:gen-class))

(defn get-project-pane
  "Returns the pane with the project tree."
  [console]
  (let [project-tree (s/tree :id :project-tree :focusable? true)
        create-new-project (fn [_]
                             (try (p/new-project (:in console) (:out console))
                               (catch Exception e
                                 (.enterLine (:view console) ""))))
        btn-group (s/horizontal-panel
                    :items [(ui/button :id :new-project-button
                                       :text (utils/get-string :new_project)
                                       :listen [:action create-new-project]
                                       :focusable? false)
                            (ui/button :id :new-file-button
                                       :text (utils/get-string :new_file)
                                       :listen [:action p/new-file]
                                       :focusable? false)
                            (ui/button :id :rename-file-button
                                       :text (utils/get-string :rename_file)
                                       :listen [:action p/rename-file]
                                       :focusable? false
                                       :visible? false)
                            (ui/button :id :import-button
                                       :text (utils/get-string :import)
                                       :listen [:action p/import-project]
                                       :focusable? false)
                            (ui/button :id :remove-button
                                       :text (utils/get-string :remove)
                                       :listen [:action p/remove-item]
                                       :focusable? false)
                            :fill-h])
        project-pane (s/vertical-panel
                       :id :project-pane
                       :items [btn-group
                               (s/scrollable project-tree)])]
    (doto project-tree
          (.setRootVisible false)
          (.setShowsRootHandles true)
          (.addTreeExpansionListener
            (reify TreeExpansionListener
              (treeCollapsed [this e] (p/remove-expansion e))
              (treeExpanded [this e] (p/add-expansion e))))
          (.addTreeSelectionListener
            (reify TreeSelectionListener
              (valueChanged [this e] (p/set-selection e))))
          (-> .getSelectionModel
              (.setSelectionMode TreeSelectionModel/SINGLE_TREE_SELECTION)))
    (shortcuts/create-mappings project-pane
                               {:new-project-button create-new-project
                                :new-file-button p/new-file
                                :rename-file-button p/rename-file
                                :import-button p/import-project
                                :remove-button p/remove-item})
    project-pane))

(defn get-repl-pane
  "Returns the pane with the REPL."
  [console]
  (let [run (fn []
              (s/request-focus! (-> console :view .getViewport .getView))
              (lein/run-repl (:process console) (:in console) (:out console)))]
    (run)
    (doto (s/config! (:view console) :id :repl-console)
      (shortcuts/create-mappings {:repl-console (fn [_] (run))}))))

(defn get-editor-pane
  "Returns the pane with the editors."
  []
  (s/card-panel :id :editor-pane :items [["" :default-card]]))

(defn get-builder-pane
  "Returns the pane with the builders."
  []
  (s/card-panel :id :builder-pane :items [["" :default-card]]))

(defn get-window-content
  "Returns the entire window with all panes."
  []
  (let [process (atom nil)
        view (ui/create-console)
        in (ui/get-console-input view)
        out (ui/get-console-output view)
        console {:process process :view view :in in :out out}]
    (s/left-right-split
      (s/top-bottom-split (get-project-pane console)
                          (get-repl-pane console)
                          :divider-location 0.8
                          :resize-weight 0.5)
      (s/top-bottom-split (get-editor-pane)
                          (get-builder-pane)
                          :divider-location 0.8
                          :resize-weight 0.5))))

(defn -main
  "Launches the main window."
  [& args]
  (s/native!)
  (SubstanceLookAndFeel/setSkin (GraphiteSkin.))
  (s/invoke-later
    ; create and show the frame
    (reset! ui/ui-root
      (doto (s/frame :title (str (utils/get-string :app_name)
                                 " "
                                 (utils/get-version))
                     :content (get-window-content)
                     :width 1152
                     :height 768
                     :on-close :exit)
        ; create the shortcut hints for the main buttons
        shortcuts/create-hints
        ; listen for keys while modifier is down
        (shortcuts/listen-for-shortcuts
          (fn [key-code]
            (case key-code
              ; enter
              10 (p/toggle-project-tree-selection)
              ; left
              37 (p/move-tab-selection -1)
              ; up
              38 (p/move-project-tree-selection -1)
              ; right
              39 (p/move-tab-selection 1)
              ; down
              40 (p/move-project-tree-selection 1)
              ; Q
              81 (if (dialogs/show-shut-down-dialog)
                   (System/exit 0)
                   true)
              ; W
              87 (editors/close-selected-editor)
              false)))
        ; update the project tree when window comes into focus
        (.addWindowListener (proxy [WindowAdapter] []
                              (windowActivated [e]
                                (ui/update-project-tree))))
        ; show the frame
        s/show!))
    ; initialize the project pane
    (ui/update-project-tree)))
