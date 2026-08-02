## ADDED Requirements

### Requirement: 同一 Board・同一更新種別は最新 intent が supersede する

Board tab controller は同一 `boardUrl` かつ同一更新種別の未完了 `Scroll`、`Pin`、または `Info` command を後続 command で supersede しなければならない（SHALL）。supersede された command は projection と canonical confirmation の対象から除外され、terminal `NoOp` で waiter を解放しなければならない（SHALL）。

#### Scenario: repeated scroll の中間値が obsolete になる

- **WHEN** 同一 Board に複数の scroll position command が canonical 通知前に受理される
- **THEN** controller は最新 scroll position だけを effective presentation に投影する
- **THEN** 同一 Board の scroll pending command は最大 1 件になる
- **THEN** supersede された各 command の waiter は `NoOp` で terminal になる

#### Scenario: 更新種別が異なる command は独立する

- **WHEN** 同一 Board に scroll、pin、resolved info command が受理される
- **THEN** controller は異なる更新種別を相互に supersede しない
- **THEN** 各更新種別の最新 projection を同時に effective presentation へ適用する

#### Scenario: 異なる Board は独立する

- **WHEN** Board A と Board B に同じ更新種別の command が受理される
- **THEN** Board A の後続 command は Board B の pending command を supersede しない
- **THEN** 各 Board は自身の最終 canonical 値が通知されるまで独立して最新 projection を維持する

### Requirement: supersede 済み write は最終 canonical state を上書きしない

Board tab controller は supersede 済み command が repository dispatch 前ならその targeted write を skip しなければならない（SHALL）。dispatch 済み command は cancel せず、後続の最新 targeted write が DB canonical state の最終値になる順序を維持しなければならない（SHALL）。

#### Scenario: load 待ちの scroll が supersede される

- **WHEN** load 完了前に同一 Board の複数 scroll command が受理される
- **THEN** controller は supersede 済み command の repository method を呼ばない
- **THEN** controller は最新 command の targeted scroll write だけを dispatch する

#### Scenario: 先行 write が既に開始している

- **WHEN** 先行 command の targeted write 開始後に同一 key の後続 command が受理される
- **THEN** controller は先行 write を cancellation race で中断しない
- **THEN** controller は後続の最新 write を先行 write の後に適用して最終 canonical 値を最新 intent にする

### Requirement: 最終 canonical 通知だけで pending が収束する

Board tab controller は Room observable query が中間値を通知せず最終値だけを通知した場合でも、最新 command を確認して当該 supersession key の pending を解放しなければならない（SHALL）。古い projection を再表示してはならない（MUST NOT）。

#### Scenario: Room が repeated scroll の最終値だけを通知する

- **WHEN** 複数の scroll write が成功し、canonical Flow が中間位置を省略して最終位置だけを emit する
- **THEN** controller は最終位置を canonical state として採用する
- **THEN** 最新 scroll command を terminal `Success` にして pending から除去する
- **THEN** supersede 済みの中間位置を presentation に再投影しない

#### Scenario: rapid pin と resolved info が最終値へ収束する

- **WHEN** 同一 Board の pin または resolved info が短時間に反復更新され、Flow が各種別の最終値だけを通知する
- **THEN** controller は各更新種別の最新 command を確認する
- **THEN** pending は解放され、effective presentation と DB canonical state は最終値で一致する

### Requirement: failure は現在の最新 intent に限定する

Board tab controller は supersede 済み command の遅延 success または failure で最新 command の lifecycle、result、projection を変更してはならない（MUST NOT）。最新 command の repository failure は terminal `Failure` とし、その projection を除去して DB canonical state へ戻さなければならない（SHALL）。

#### Scenario: supersede 済み command が遅れて失敗する

- **WHEN** supersede 済み command の repository call が後から failure を返す
- **THEN** supersede 済み waiter の terminal `NoOp` は変化しない
- **THEN** 最新 command は pending と projection に残る

#### Scenario: 最新 command が失敗する

- **WHEN** 同一 supersession key の最新 command の targeted write が failure を返す
- **THEN** controller は最新 waiter を terminal `Failure` にする
- **THEN** 当該 command を pending から除去する
- **THEN** presentation は supersede 済み projection を復活させず現在の DB canonical state を表示する

### Requirement: 既存 Board 契約を維持する

この correction は targeted per-row persistence と DB canonical source of truth を維持し、full-list persistence を追加してはならない（MUST NOT）。また Ensure、Delete、adjacent close selection、atomic presentation、および明示的 Deep Link terminal result の挙動を変更してはならない（MUST NOT）。

#### Scenario: rapid update correction の回帰境界

- **WHEN** rapid Board update correction が適用される
- **THEN** scroll、pin、info は既存の targeted repository method を使用する
- **THEN** `saveOpenBoardTabs` は呼ばれない
- **THEN** close、selection、page animation、Deep Link の既存 test は同じ期待値で成功する

#### Scenario: Thread は対象外である

- **WHEN** Board rapid update correction を実装する
- **THEN** Thread coordinator、Thread persistence、Thread test、Thread OpenSpec の挙動とファイルを変更しない
