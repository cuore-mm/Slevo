## Context

スレッド画面のスクロール位置保存は `ThreadScaffold` から `ThreadViewModel.updateThreadScrollPosition` を経由し、`ThreadTabCoordinator.updateThreadScrollPosition` で実行される。現在の Coordinator は `TabsRepository.observeOpenThreadTabs().first()` で開いているスレッドタブ一覧を取得し、対象 `threadId` の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` だけを `copy` した一覧を `TabsRepository.saveOpenThreadTabs()` に渡している。

`saveOpenThreadTabs()` はタブ一覧全体の保存を担当する処理であり、並び順、pin 状態、タブ集合、スレッド状態保存、不要状態の整理など、スクロール位置以外の関心も含む。Issue 490 対応で保存頻度は `sample(200ms)` と離脱時保存に整理されたが、スクロール位置だけの更新が read-map-save-all 経路に依存している点は残っている。

## Goals / Non-Goals

**Goals:**

- スクロール位置だけを保存する場合は、指定 `threadId` のスクロール列だけを更新する。
- `ThreadTabCoordinator.updateThreadScrollPosition` からタブ一覧全体の再保存をなくす。
- タブ追加、削除、並び替え、pin 切替など、タブ一覧の構造を変更する保存経路は維持する。
- DB スキーマ、既存保存データ、スクロール復元仕様を変更しない。
- スクロール保存とタブ一覧保存の責務を分離し、並行更新時の影響範囲を小さくする。

**Non-Goals:**

- レス番号ベースの新しいスクロールアンカー永続化は扱わない。
- `open_thread_tabs` のスキーマ変更やマイグレーションは行わない。
- タブ一覧保存処理全体の再設計は行わない。
- スクロール保存頻度や `BbsRouteScaffold` の保存タイミングは変更しない。

## Decisions

### 1. スクロール位置専用の Repository メソッドを追加する

`TabsRepository` に、指定 `threadId` の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` だけを更新する suspend 関数を追加する。`ThreadTabCoordinator.updateThreadScrollPosition` は、一覧取得と `saveOpenThreadTabs()` を使わず、この専用メソッドを呼び出す。

代替案として Coordinator 内で一覧全体保存を維持する方法があるが、スクロール位置更新のたびにタブ一覧全体の保存責務へ入るため、並び順や pin 状態など他のタブ固有状態との競合範囲が広い。専用メソッドは更新対象を scroll columns に限定できる。

### 2. DAO は単一行・単一責務の UPDATE を提供する

`OpenThreadTabDao` に、`threadId` を条件として `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` を更新する `@Query` を追加する。戻り値は更新件数を表せる `Int` を候補とし、Repository では存在しないタブへの遅延保存を正常系として扱う。

スクロール保存は UI の補助状態であり、タブが閉じられた後に遅れて保存要求が届く可能性がある。その場合、0件更新は無視できる結果であり、例外扱いにしない。

### 3. `saveOpenThreadTabs()` はタブ一覧構造の保存に限定して残す

`saveOpenThreadTabs()` は、タブの追加、削除、並び替え、pin 状態変更など、一覧全体または複数行の整合性が必要な操作で引き続き使用する。本変更はスクロール位置だけの高頻度更新経路を分離するものであり、既存の一覧保存機能を置き換えない。

この分離により、スクロール保存は `sortOrder` や `isPinned` を更新しなくなり、別操作が同時に行われた場合でも不要な上書きリスクを小さくできる。

### 4. テストは更新対象列と呼び出し経路を確認する

DAO / Repository レベルでは、対象 `threadId` の scroll columns だけが更新され、他タブの scroll columns、`sortOrder`、`isPinned` が変わらないことを確認する。Coordinator レベルでは、`updateThreadScrollPosition` が一覧取得・一覧保存ではなく専用 Repository メソッドを呼ぶことを確認する。

既存のスクロール復元やタブ一覧表示の仕様は、保存された scroll columns を読み出す経路が変わらないため維持される。

## Risks / Trade-offs

- [Risk] タブを閉じた直後にスクロール保存が到着し、更新対象行が存在しない。 → DAO の更新件数 0 を正常な no-op として扱う。
- [Risk] `saveOpenThreadTabs()` と専用 scroll update が近接して実行されると、後勝ちで値が変わる可能性がある。 → スクロール位置は最新値が上書きされる補助状態として扱い、専用 UPDATE で影響列を scroll columns に限定する。
- [Risk] Repository API が増え、保存経路が複数になる。 → メソッド名と KDoc で「スクロール位置だけの更新」と「タブ一覧構造の保存」を明確に分ける。
- [Risk] Room の Flow 通知回数は更新のたびに発生する。 → 既存の `sample(200ms)` と重複保存抑制を維持し、本変更では 1 回あたりの更新範囲を小さくする。
