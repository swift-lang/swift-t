;;; swift-t-mode.el --- Major mode for Swift/T -*- lexical-binding: t -*-

;; Copyright 2013 University of Chicago and Argonne National Laboratory
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License

;;; Commentary:

;; Syntax highlighting and indentation for Swift/T (*.swift) files.
;;
;; It is named swift-t-mode to avoid clashing with swift-mode, the
;; Emacs package for Apple's Swift language, which also claims *.swift.
;;
;; Installation:
;;
;;   Put this file in a directory on your load-path, for example
;;   ~/.emacs.d/lisp, and add to your init file (~/.emacs or
;;   ~/.emacs.d/init.el):
;;
;;     (add-to-list 'load-path "~/.emacs.d/lisp")
;;     (autoload 'swift-t-mode "swift-t-mode" "Swift/T mode" t)
;;     (add-to-list 'auto-mode-alist '("\\.swift\\'" . swift-t-mode))
;;
;;   Optionally byte-compile it, for faster loading:
;;
;;     emacs --batch -f batch-byte-compile swift-t-mode.el
;;
;;   If you also edit Apple Swift, skip the auto-mode-alist line and
;;   select the mode per file instead, with M-x swift-t-mode or a
;;   first line such as:
;;
;;     // -*- mode: swift-t -*-
;;
;; Features:
;;
;;   - Highlights reserved words, types, constants, annotations
;;     (@pure), preprocessor directives, and the names of functions
;;     being defined.  match, arguments, and flags are highlighted
;;     only where they are special: match as a route branch label,
;;     arguments() and flags() at the start of a statement.
;;   - Indents by braces, with branch bodies in switch and route
;;     statements indented under their case, pattern, match, or
;;     default label.  TAB indents the current line, and
;;     M-x indent-region indents a selection.
;;   - Comments with // via M-; (comment-dwim).
;;
;; Customization:
;;
;;   swift-t-indent-offset   columns per indentation level (default 2)
;;
;;   For example:  (setq swift-t-indent-offset 4)

;;; Code:

(defgroup swift-t nil
  "Major mode for Swift/T."
  :group 'languages)

(defcustom swift-t-indent-offset 2
  "Number of columns for each level of indentation in Swift/T."
  :type 'integer
  :safe #'integerp
  :group 'swift-t)

;; Keep these lists in sync with the "Reserved words" section of the
;; Swift/T Guide and the lexer in stc/code/src/exm/stc/ast/ExM.g
(defconst swift-t-keywords
  '("app" "builtin" "case" "const" "deep" "default" "else" "for"
    "foreach" "global" "if" "import" "in" "iterate" "pattern" "pragma"
    "route" "switch" "type" "typedef" "until" "wait")
  "Swift/T reserved words that start or structure statements.")

(defconst swift-t-constants
  '("true" "false" "NaN" "inf" "stdin" "stdout" "stderr")
  "Swift/T reserved words that are values.")

(defconst swift-t-types
  '("int" "float" "string" "boolean" "file" "url" "blob" "void"
    "bag" "location")
  "Swift/T built-in type names.")

(defconst swift-t--label-regexp
  "\\(case\\|default\\|pattern\\)\\_>\\|match[ \t]+\""
  "A branch label in switch or route, matched at the indentation.")

(defvar swift-t-font-lock-keywords
  `((,(regexp-opt swift-t-keywords 'symbols) . font-lock-keyword-face)
    (,(regexp-opt swift-t-constants 'symbols) . font-lock-constant-face)
    (,(regexp-opt swift-t-types 'symbols) . font-lock-type-face)
    ;; Contextual keywords, special only in position:
    ;; match "glob": in a route body
    ("^[ \t]*\\(match\\)[ \t]+\"" 1 font-lock-keyword-face)
    ;; arguments(...); and flags(...); at the start of a statement
    ("^[ \t]*\\(arguments\\|flags\\)[ \t]*(" 1 font-lock-keyword-face)
    ;; Annotations such as @pure, @par=4
    ("@[A-Za-z_][A-Za-z0-9_]*" . font-lock-preprocessor-face)
    ;; C preprocessor directives
    ("^[ \t]*#[ \t]*[a-z]+" . font-lock-preprocessor-face)
    ;; Name of a function being defined: app (...) name(
    ;; or (outputs) name(inputs) {
    ("\\_<app\\_>[ \t]*([^)]*)[ \t]*\\([A-Za-z_][A-Za-z0-9_]*\\)"
     1 font-lock-function-name-face)
    ("^[ \t]*([^)]*)[ \t]*\\([A-Za-z_][A-Za-z0-9_]*\\)[ \t]*("
     1 font-lock-function-name-face))
  "Highlighting for `swift-t-mode'.")

(defvar swift-t-mode-syntax-table
  (let ((table (make-syntax-table)))
    ;; C and C++ style comments
    (modify-syntax-entry ?/ ". 124b" table)
    (modify-syntax-entry ?* ". 23" table)
    (modify-syntax-entry ?\n "> b" table)
    (modify-syntax-entry ?\" "\"" table)
    (modify-syntax-entry ?\\ "\\" table)
    (modify-syntax-entry ?_ "_" table)
    (dolist (c '(?+ ?- ?% ?< ?> ?& ?| ?= ?!))
      (modify-syntax-entry c "." table))
    table)
  "Syntax table for `swift-t-mode'.")

(defun swift-t--block-is-switch (open-pos)
  "Non-nil if the brace at OPEN-POS opens a switch or route body."
  (save-excursion
    (goto-char open-pos)
    ;; Skip back over the header: (...) and the keyword
    (skip-chars-backward " \t\n")
    (when (eq (char-before) ?\))
      (ignore-errors (backward-sexp)))
    (skip-chars-backward " \t\n")
    (looking-back "\\_<\\(switch\\|route\\)" (line-beginning-position))))

(defun swift-t--after-label (open-pos)
  "Non-nil if a branch label precedes this line in the block at OPEN-POS."
  (save-excursion
    (beginning-of-line)
    (let ((found nil))
      (while (and (not found)
                  (> (point) open-pos)
                  (= (forward-line -1) 0))
        (back-to-indentation)
        (when (and (> (point) open-pos)
                   (eq (nth 1 (syntax-ppss)) open-pos)
                   (looking-at swift-t--label-regexp))
          (setq found t))
        (beginning-of-line))
      found)))

(defun swift-t--indentation ()
  "Column for the current line."
  (save-excursion
    (back-to-indentation)
    (let* ((ppss (syntax-ppss))
           (open (nth 1 ppss)))
      (cond
       ;; Inside a comment or string: leave it alone
       ((or (nth 3 ppss) (nth 4 ppss))
        (current-indentation))
       ;; Inside ( or [
       ((and open (memq (char-after open) '(?\( ?\[)))
        (let ((closing (looking-at "[])]")))
          (save-excursion
            (goto-char open)
            (if (looking-at ".[ \t]*$")
                ;; Opener ends its line, as in a builtin's Tcl body:
                ;; indent one level from that line
                (+ (current-indentation)
                   (if closing 0 swift-t-indent-offset))
              ;; Otherwise line up after the opening character
              (1+ (current-column))))))
       ;; Top level
       ((null open) 0)
       ;; Inside { : one level in from the line that opened the block,
       ;; so that nesting inside a branch body is handled
       (t
        (let ((base (save-excursion
                      (goto-char open)
                      (current-indentation)))
              (closing (looking-at "}"))
              (label (looking-at swift-t--label-regexp)))
          (cond
           (closing base)
           ;; Statements in a switch or route branch go under the label;
           ;; lines before the first label stay at label level
           ((and (not label)
                 (swift-t--block-is-switch open)
                 (swift-t--after-label open))
            (+ base (* 2 swift-t-indent-offset)))
           (t (+ base swift-t-indent-offset)))))))))

(defun swift-t-indent-line ()
  "Indent the current line as Swift/T code."
  (interactive)
  (let ((col (swift-t--indentation))
        (offset (- (current-column) (current-indentation))))
    (indent-line-to col)
    (when (> offset 0)
      (forward-char offset))))

;;;###autoload
(define-derived-mode swift-t-mode prog-mode "Swift/T"
  "Major mode for editing Swift/T programs."
  :syntax-table swift-t-mode-syntax-table
  (setq-local font-lock-defaults '(swift-t-font-lock-keywords))
  (setq-local comment-start "// ")
  (setq-local comment-end "")
  (setq-local comment-start-skip "//+[ \t]*\\|/\\*+[ \t]*")
  (setq-local indent-line-function #'swift-t-indent-line)
  (setq-local indent-tabs-mode nil)
  (setq-local electric-indent-chars
              (append '(?} ?:) electric-indent-chars)))

(provide 'swift-t-mode)

;;; swift-t-mode.el ends here
