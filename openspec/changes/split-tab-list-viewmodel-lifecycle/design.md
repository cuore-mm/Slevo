## Context

現在の `TabsViewModel` は `MainActivity` で `by viewModels()` により生成され、Activity スコープでアプリ全体から参照されている。この ViewModel は、開いている板/スレッドタブ、タブ永続化、スレッドタブ更新、ページ状態、`BoardViewModel` / `ThreadViewModel` のキャッシュに加えて、タブ一覧画面だけで必要な検索モード、検索クエリ、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログの表示状態も保持している。

タブセッションは画面をまたいで維持する必要がある一方、検索や長押し選択などの一時 UI 状態はタブ一覧画面の表示ライフサイクルに合わせて初期化される方が自然である。そのため、共有すべきタブセッション状態と画面固有 UI 状態を分離する。

## Goals / Non-Goals

**Goals:**

- タブ一覧画面固有の UI 状態を、タブ一覧画面の NavBackStackEntry / Composable ライフサイクルに紐付く ViewModel で管理する。
- 既存の Activity スコープ ViewModel は、アプリ全体で共有するタブセッション管理に責務を寄せる。
- 検索、長押し選択、詳細 BottomSheet、削除待ちなどの既存ユーザー挙動を維持する。
- 子 `BoardViewModel` / `ThreadViewModel` の再利用とタブ永続化の動作を維持する。
- UI 状態収集は画面上位で行い、子 Composable へ必要な値と操作を渡す既存方針を維持する。

**Non-Goals:**

- タブ一覧の見た目、カードレイアウト、スワイプ削除、固定タブ表示、更新処理の仕様変更。
- Room / DataStore のスキーマ変更。
- `BoardViewModel` / `ThreadViewModel` の生成方式そのものの変更。
- DeepLink、履歴、ブックマークからタブを開く機能の仕様変更。

## Decisions

### 1. Activity スコープの ViewModel はタブセッション管理として残す

`TabsViewModel` をそのままタブ一覧画面スコープへ移すのではなく、共有セッション状態の所有者として Activity スコープに残す。開いているタブ一覧、タブ追加/削除/固定、スレッド更新、ページ状態、`TabViewModelRegistry` は、タブ一覧画面以外からも利用されるため、画面スコープにすると状態喪失や子 ViewModel の再生成が発生しやすい。

代替案として `TabsViewModel` 全体をタブ一覧画面の ViewModel にする案があるが、DeepLink や各画面からのタブ操作との依存関係が崩れやすく、タブをブラウザ的なアプリ内セッションとして扱う既存設計に合わないため採用しない。

### 2. タブ一覧画面専用 ViewModel を新設する

タブ一覧画面専用に `TabListViewModel` 相当の ViewModel を導入する。この ViewModel は `TabsViewModel` が提供するセッション状態を入力として参照し、画面固有状態を合成して `TabListUiState` 相当の状態を公開する。

画面専用 ViewModel の所有候補は以下とする。

- 検索モードと検索クエリ
- 検索済み板/スレッドタブ一覧
- 長押し選択中のタブと bounds
- 削除待ちタブ
- 詳細 BottomSheet 表示対象と表示フラグ
- URL入力ダイアログの表示・検証・エラー状態

代替案として Composable の `rememberSaveable` で一時状態を保持する案があるが、長押し選択や BottomSheet は複数 Composable にまたがるため、画面専用 ViewModel に集約した方が状態更新とテストの境界が明確になる。

### 3. セッション ViewModel への操作は明示的なコマンド委譲にする

`TabListViewModel` はタブの追加/削除/固定、スレッド更新、ページ切替、子 ViewModel 取得などのセッション操作を自分で実装せず、Activity スコープのセッション ViewModel へ委譲する。これにより、永続化と共有状態の正本を二重化しない。

画面側からは、タブ一覧画面固有の操作（検索開始、検索終了、長押し選択、詳細表示、削除確認）と、セッション操作（タブを閉じる、固定を切り替える、更新する）を区別して扱う。

### 4. 移行は段階的に行う

まず検索状態と検索フィルタを画面専用 ViewModel へ移し、その後に長押し選択、詳細 BottomSheet、削除待ち、URL入力ダイアログを移す。最後に `TabsUiState` から画面固有状態を削除し、命名や受け渡しを整理する。

段階移行により、既存のタブ一覧 UI 仕様、スレッド更新、ナビゲーション、子 ViewModel キャッシュへの影響を小さくする。

## Risks / Trade-offs

- [Risk] `TabsViewModel` と `TabListViewModel` の両方が同じ状態を持ち、正本が曖昧になる。 → 画面固有状態は `TabListViewModel`、タブセッション状態は Activity スコープ ViewModel という境界を tasks で明示し、重複するフィールドは段階的に削除する。
- [Risk] ViewModel を2つ扱うことで Composable の引数やイベントが増える。 → 画面上位で状態とイベントを束ね、子 Composable には必要な値と操作だけを渡す。
- [Risk] タブ一覧から離れた際に検索状態などがリセットされ、既存挙動との差分として見える可能性がある。 → 仕様として画面固有状態は画面ライフサイクルに従うことを明文化し、タブ本体や更新状態は維持する。
- [Risk] スレッド更新中にタブ一覧画面を離れた場合の進捗表示が失われる。 → 更新処理と進捗の正本はセッション ViewModel に残し、画面復帰時に再収集して表示する。
- [Risk] Hilt のスコープ指定や Navigation の ViewModel 取得箇所を誤ると、意図せず Activity スコープ化または再生成される。 → `TabListViewModel` はタブ一覧画面の NavBackStackEntry から取得し、Activity スコープ ViewModel とは取得箇所を分ける。

## Migration Plan

1. `TabsViewModel` の状態をセッション状態と画面固有 UI 状態に分類する。
2. `TabListViewModel` と `TabListUiState` を追加し、検索状態とフィルタ済み一覧を移す。
3. タブ一覧画面の上位 Composable で Activity スコープのセッション ViewModel と画面スコープの `TabListViewModel` を取得する。
4. 長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログを `TabListViewModel` へ移す。
5. `TabsViewModel` から移行済みの画面固有状態とイベントを削除し、セッション操作だけを残す。
6. 既存テストを更新し、画面固有状態の単体テストと既存セッション管理テストを分離する。

Rollback は、移行対象を `TabsViewModel` 側の既存状態へ戻すことで可能とする。DB スキーマ変更を行わないため、データ移行や永続化ロールバックは不要。

## Open Questions

- Activity スコープ ViewModel の名称を `TabsViewModel` のままにするか、責務に合わせて `TabSessionViewModel` へリネームするか。
- URL入力ダイアログをタブ一覧専用 UI とみなすか、他画面からも開く導線がある場合にセッション側へ残すか。
