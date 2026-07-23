## ADDED Requirements

### Requirement: タブ page の canonical 定義
システムはタブ一覧 pager の page 数、各 page index の意味、およびバックアップ復元時の有効 index 判定を、同一の canonical page 定義から導出しなければならない（MUST）。canonical 定義の現在の順序は board が index 0、thread が index 1 であり、新しい page は既存 index の意味を変えず末尾へ追加しなければならない（MUST）。

#### Scenario: 現在の pager page を共有定義から構成する
- **WHEN** タブ一覧 pager を構成する
- **THEN** pager は canonical 定義から 2 page を導出し、index 0 に board、index 1 に thread を表示する

#### Scenario: 将来 page を末尾追加する
- **WHEN** canonical 定義へ新しい page を既存 entry の後ろに追加する
- **THEN** pager page count とバックアップの有効 index 上限は同じ定義から更新され、バックアップ validator に別の範囲 literal の編集を必要としない

### Requirement: 復元 tabs page の範囲検証
システムは `datastore/tabs.json` の整数 `lastSelectedTabsPage` が canonical tab page 定義の有効 index である場合に限り、そのバックアップを tabs JSON validation に合格させなければならない（MUST）。範囲外の場合は既存の無効バックアップ処理を使用し、preview または pending restore を作成してはならない（MUST NOT）。

#### Scenario: 最小有効 page を受け付ける
- **WHEN** `lastSelectedTabsPage` が canonical 定義の最小 index 0 である
- **THEN** システムは tabs page の範囲検証に合格させ、整数値 0 を変更せず保持する

#### Scenario: 最大有効 page を受け付ける
- **WHEN** `lastSelectedTabsPage` が canonical page count より 1 小さい index である
- **THEN** システムは tabs page の範囲検証に合格させ、その整数値を変更せず保持する

#### Scenario: 負数 page を拒否する
- **WHEN** `lastSelectedTabsPage` が 0 未満である
- **THEN** システムは既存の invalid tabs JSON 経路でバックアップを拒否し、preview または pending restore を作成しない

#### Scenario: page count 以上を拒否する
- **WHEN** `lastSelectedTabsPage` が canonical page count 以上である
- **THEN** システムは既存の invalid tabs JSON 経路でバックアップを拒否し、preview または pending restore を作成しない

### Requirement: タブ page の保存形式互換性
システムは canonical 定義の導入後も `lastSelectedTabsPage` を既存の整数 index として `datastore/tabs.json` および `last_selected_page` に保存し、backup format version、field 名、DataStore key、既存 index の意味を変更してはならない（MUST NOT）。

#### Scenario: 既存 backup の page index を復元する
- **WHEN** backup format version 1 の tabs JSON が `lastSelectedTabsPage` 0 または 1 を含む
- **THEN** システムは形式 migration を行わず、0 を board、1 を thread として扱う
