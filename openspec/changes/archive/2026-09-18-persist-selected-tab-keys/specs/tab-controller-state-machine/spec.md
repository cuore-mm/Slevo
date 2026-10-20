## MODIFIED Requirements

### Requirement: atomic presentation と deterministic selection repair
システムは effective tabs と selection resolution を一つの `TabPresentationState` として公開し、有効選択、既知の一時不在、確定無効、空、初期／restore を決定論的に処理することを SHALL 要求する。初回canonical一覧と永続selected keyの読込が完了する前にloaded presentationを公開せず、有効な復元keyまたは末尾へ補正したkeyを最初のloaded emissionへ反映しなければならないMUST。

#### Scenario: 有効選択
- **WHEN** selected key が effective tabs に存在する
- **THEN** presentation は同じ key の `Selected` を公開し target page を表示する

#### Scenario: 既知の一時不在
- **WHEN** selected key が tabs にないが対応 pending cause が生存する
- **THEN** presentation は `PendingMissing` を公開し、現在 page/content を保持して page 0 へ移動しない

#### Scenario: selected tab close
- **WHEN** 選択中 tab の close が effective state に適用され残り tab が存在する
- **THEN** 削除前 index の同位置、範囲外なら末尾を一度だけ選択する

#### Scenario: その他の確定無効と restore
- **WHEN** loaded non-empty state の復元selected keyがnullまたはpending causeなしで不在である
- **THEN** Controllerは末尾keyへrepairし、同じemissionで`Selected`を公開する

#### Scenario: 初回復元完了前
- **WHEN** canonical一覧または永続selected keyの初回読込が完了していない
- **THEN** Controllerはloaded selectionを公開せず初期読込状態を維持する

#### Scenario: zero tabs
- **WHEN** loaded effective tabs が0件になる
- **THEN** selected keyをnullにし`Empty`を公開してtab contentを表示しない
