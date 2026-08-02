# thread-rapid-mutation-liveness Specification

## Purpose
TBD - created by archiving change fix-thread-rapid-write-liveness. Update Purpose after archive.
## Requirements
### Requirement: 両立しない同一 Thread mutation は後続成功で先行 waiter を終端する

Thread tab controller は、同一 Thread の後続 targeted write が成功し、先行 operation の canonical 条件と両立しない最終 intent を確立した場合、先行 operation を superseded terminal 状態へ進めなければならない（SHALL）。後続 write が失敗した場合は先行 operation を supersede してはならない（MUST NOT）。

#### Scenario: repeated pin の中間値が通知されない

- **WHEN** 同一 Thread の複数 pin toggle write が成功し、Room が中間 pin 値を省略して最終値だけを通知する
- **THEN** 後続成功 Pin は先行 Pin waiter を `Unit` 成功で有限に終端する
- **THEN** 最新 Pin は最終 canonical pin 値で確認される
- **THEN** optimistic pending はすべて除去され、presentation は DB canonical state に一致する

#### Scenario: 後続 pin が失敗する

- **WHEN** 先行 Pin write が成功し、同一 Thread の後続 Pin write が失敗する
- **THEN** controller は先行 Pin を supersede しない
- **THEN** 先行 Pin は自身の canonical 値で完了し、後続 Pin waiter だけが失敗する

### Requirement: Ensure と Delete は最終存在 intent を安全に決める

Thread tab controller は同一 Thread の successful Ensure と Delete を受理順で適用し、後の successful lifecycle write を最終存在 intent としなければならない（SHALL）。中間存在または不在 snapshot が省略されても、先行 waiter と pending projection を残し続けてはならない（MUST NOT）。

#### Scenario: Ensure の直後に Delete が成功する

- **WHEN** 同一 Thread の Ensure write に続いて Delete write が成功し、Room が一時的な存在状態を通知せず最終不在だけを通知する
- **THEN** 先行 Ensure は `-1` で終端する
- **THEN** Delete は最終 canonical absence を確認して既存の selection repair と対象 session/runtime cleanup を実行する
- **THEN** 先行 Ensure の遅延完了は tab を再投影または再作成しない

#### Scenario: Delete の直後に Ensure が成功する

- **WHEN** 同一 Thread の Delete write に続いて Ensure write が成功し、Room が一時的な不在状態を通知せず最終存在だけを通知する
- **THEN** 先行 Delete waiter は有限に終端する
- **THEN** superseded Delete は selection repair または対象 session/runtime cleanup を実行しない
- **THEN** Ensure は最終 canonical presence の index を返し、tab は存在状態に収束する

#### Scenario: 後続 lifecycle write が失敗する

- **WHEN** 先行 Ensure または Delete の後に両立しない lifecycle write が受理され、その後続 write が失敗する
- **THEN** controller は先行 lifecycle operation を supersede しない
- **THEN** 先行 operation は自身の canonical 条件と既存 cleanup 契約に従って完了する
- **THEN** 後続 waiter だけが失敗する

### Requirement: supersession は operation の意味と Thread identity に限定する

Controller は successful Pin で同一 Thread の先行 Pin だけを、successful Delete で同一 Thread の先行 Ensure・Pin・Info だけを、successful Ensure で同一 Thread の先行 Delete だけを supersede しなければならない（SHALL）。異なる Thread または両立する operation を supersede してはならない（MUST NOT）。

#### Scenario: 異なる Thread は独立して進む

- **WHEN** Thread A と Thread B に rapid mutation が同時に pending である
- **THEN** Thread A の successful write または canonical snapshot は Thread B の waiter、pending、projectionを終端または除去しない
- **THEN** Thread B は Thread A の canonical confirmation を待たずに自身の最終状態へ完了できる

#### Scenario: 同じ存在条件を共有する operation

- **WHEN** 同一 Thread に Ensure→Ensure、Delete→Delete、または Info→Info が受理される
- **THEN** controller は両 operation を競合 lifecycle として supersede しない
- **THEN** 共有する最終 identity presence または absence snapshot で各 waiter を確認できる

### Requirement: dispatch 済み write と既存安全境界を維持する

Controller は supersede 対象の write が既に repository へ dispatch 済みの場合にその write を cancel してはならず（MUST NOT）、後続 targeted write を既存 `DatabaseWriteGate` 順序で適用して最終 DB canonical state を決めなければならない（SHALL）。本 correction は broad/global Flow-confirmation barrier、full-list persistence、exact metadata confirmation を追加してはならない（MUST NOT）。

#### Scenario: obsolete write が既に dispatch 済みである

- **WHEN** 先行 same-Thread write が開始または commit した後に両立しない後続 write が成功する
- **THEN** controller は先行 repository call を cancellation race で中断しない
- **THEN** 後続 targeted write が先行 write の後に適用されて最終 canonical state を決める
- **THEN** superseded 先行 completion は後続 pending または projection を除去しない

#### Scenario: correction の回帰境界

- **WHEN** rapid Thread mutation correction が適用される
- **THEN** 通常 mutation は既存 targeted repository method だけを使用し、bulk replacement を呼ばない
- **THEN** Room Flow だけが canonical tabs を供給する
- **THEN** placeholder-safe metadata merge、retained close lifetime、明示的 Deep Link result、atomic tab/selection presentation を維持する
- **THEN** Board coordinator と deferred P2 behavior を変更しない
