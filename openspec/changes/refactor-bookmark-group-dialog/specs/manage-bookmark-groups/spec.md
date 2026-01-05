## ADDED Requirements
### Requirement: 共通グループ編集コントローラ
システムは Board/Thread/BookmarkList の各画面で共通の GroupDialogState と GroupDialogController を用いてグループ編集とダイアログ制御を提供しなければならない (SHALL)。

#### Scenario: 一覧と単一画面で同一の編集フロー
- **WHEN** ユーザーが板/スレッド画面またはブックマーク一覧でグループ編集を開始する
- **THEN** 共通コントローラ経由で同一の編集フローと状態更新が行われる
