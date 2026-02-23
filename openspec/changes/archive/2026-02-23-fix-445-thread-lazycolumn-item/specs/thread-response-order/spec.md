## ADDED Requirements

### Requirement: スレッド一覧の表示キー一意性
システムは、THREAD画面のレス一覧を描画する際、同一更新セッション内で同じレス番号が複数回表示される場合でも、`LazyColumn` の各表示行に一意なキーを割り当てなければならない（SHALL）。
この要件は TREE 表示および NUMBER 表示の両方に適用されなければならない（SHALL）。

#### Scenario: TREE表示で複数更新グループがある場合もキーが重複しない
- **WHEN** TREE表示でスレッドを複数回更新し、既存レスと新着グループの組み合わせにより同一レス番号が一覧内へ再登場する
- **THEN** 一覧内の各表示行キーは重複せず、描画処理は継続される

#### Scenario: キー重複が発生しないためクラッシュしない
- **WHEN** ユーザーがTHREAD画面を開いたまま更新を繰り返す
- **THEN** `Key "..." was already used` に起因する `IllegalArgumentException` は発生しない
