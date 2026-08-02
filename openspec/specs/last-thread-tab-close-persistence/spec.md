# last-thread-tab-close-persistence Specification

## Purpose
TBD - created by archiving change fix-last-thread-tab-close-persistence. Update Purpose after archive.
## Requirements
### Requirement: 確定した最後のスレッドタブ close は画面寿命から独立して完了する
システムは、`ThreadScaffold` でユーザーが確定したスレッドタブ close の所有権を、保留削除投影が空タブ遷移を起こす前に Activity-retained な `TabSessionStore` scope へ移譲しなければならない（MUST）。Composition の終了だけを理由に、開始済みの Delete intent、repository 書き込み、Room 正規確認をキャンセルしてはならない（MUST NOT）。

#### Scenario: 遅延書き込み中に最後のタブ投影が空になる
- **WHEN** 最後の 1 タブに対する確定 close が retained scope に受け付けられ、Delete の保留投影によって `openThreadTabs` が空になり、repository 書き込みが待機中のまま既存の `onEmptyTabs` が画面を戻す
- **THEN** Composition scope が終了しても Delete intent はキャンセルされず、書き込み待機が解消した後に Room 正本で削除を確認して完了する

#### Scenario: retained store 自体が終了する
- **WHEN** Delete intent の完了前に `TabSessionStore.close()` によって retained scope 自体が終了する
- **THEN** システムは既存の structured cancellation に従って未完了 close をキャンセルでき、store の lifetime を越える孤立 coroutine を残さない

### Requirement: 空タブ遷移と DB 正本・FIFO 契約を維持する
システムは、close 受付後の既存の保留投影、空タブ navigation、DB-canonical な Room snapshot、単一 FIFO mutation queue の順序を維持しなければならない（MUST）。navigation を削除 commit 後まで待たせる新規 UI 状態を導入してはならない（MUST NOT）。

#### Scenario: retained close の受付後に保留投影が空になる
- **WHEN** 最後のタブの Delete intent が FIFO worker に登録される
- **THEN** 既存どおり保留投影は直ちに `openThreadTabs` を空にし、既存 `onEmptyTabs` は遷移できるが、その時点で delete completion の所有者は Composition scope ではない

#### Scenario: delete が正規 snapshot で確認される
- **WHEN** repository delete の後に Room Flow が対象タブを含まない新しい正規 snapshot を通知する
- **THEN** システムは pending Delete を除去し、選択 key を null に更新し、同じ delete を重複実行せず FIFO の次 intent へ進む

### Requirement: 放棄された deep link の caller cancellation を維持する
システムは、確定 close 専用の ownership transfer を thread deep link の登録・選択 intent に適用してはならず（MUST NOT）、deep link 呼び出し元がキャンセルされた場合の既存の処理停止契約を維持しなければならない（MUST）。

#### Scenario: deep link 呼び出し元が登録完了前に終了する
- **WHEN** thread deep link の caller が readiness、登録、または正規確認の待機中にキャンセルされる
- **THEN** 既存どおり deep link intent は caller cancellation を受け、選択と navigation を継続しない
