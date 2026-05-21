## Context

板画面の `BoardViewModel.loadData` とスレ画面の `ThreadViewModel.loadData` は、読み込み処理中の例外を捕捉しても UI に通知しない経路がある。特に板更新は `refreshThreadList` の戻り値が `false` の場合も利用者へ通知されないため、読み込み失敗が「何も起きない」ように見える。

既存コードでは、画面側で `Toast.makeText` を実行する例があり、スレ画面では画像保存用に `MutableSharedFlow` の one-shot event を `ThreadScaffold` が収集して Toast を出している。このパターンに合わせると、ViewModel が Android UI API に直接依存せずに読み込み失敗を通知できる。

## Goals / Non-Goals

**Goals:**
- 板画面の読み込み失敗を Toast で通知する。
- スレ画面の読み込み失敗を Toast で通知する。
- 例外発生時と失敗結果返却時の両方を通知対象にする。
- 詳細な失敗原因は `Timber` へ記録し、Toast は短く分かりやすい文言にする。
- Toast は one-shot event として発行し、画面再コンポーズで重複表示しない。

**Non-Goals:**
- この変更では Snackbar や画面内エラー表示へ切り替えない。
- 通信層・DB層のエラー型を全面的に再設計しない。
- 自動更新や裏側の同期処理全般の通知ポリシーは扱わない。
- 画像保存や URL 不正時の既存 Toast 文言・イベントは変更しない。

## Decisions

### 1. ViewModel は one-shot UI event を発行する

板画面とスレ画面それぞれに読み込みエラー Toast 用の event flow を追加する。ViewModel は `MutableSharedFlow(extraBufferCapacity = 1)` などで `ShowToast(message)` を発行し、Scaffold 側が `LaunchedEffect(viewModel)` で収集して `Toast.makeText` を実行する。

代替案として UIState に `errorMessage` を持たせる方法がある。しかし Toast は一度だけ表示する副作用であり、UIState に残すと再コンポーズや画面復元で重複表示しやすい。one-shot event の方が既存の画像保存 Toast とも整合する。

### 2. Toast 文言は画面単位の固定文言から始める

板画面では「板の読み込みに失敗しました」、スレ画面では「スレッドの読み込みに失敗しました」のような短い固定文言を表示する。例外詳細や HTTP ステータスは `Timber` に出力する。

代替案として例外メッセージをそのまま表示する方法があるが、技術的な文言や URL が表示される可能性があり、ユーザー向けの読みやすさが低い。詳細はログへ分離する。

### 3. 例外と失敗戻り値を同じ通知対象にする

`catch (_: Exception)` は `catch (e: Exception)` に変更してログ出力し、Toast event を発行する。板読み込みのように repository が `false` を返す経路では、戻り値を確認して失敗 Toast を発行する。

スレ読み込みで repository が null や失敗結果を返す経路がある場合も、例外と同じ UI event に集約する。ロード状態解除は既存どおり `finally` または失敗分岐で確実に行う。

### 4. 重複 Toast は最小限に抑える

1 回の読み込み試行につき最大 1 回の Toast event を発行する。例外発生後にさらに `false` 扱いされるような経路では、二重発行しないように分岐を整理する。

## Risks / Trade-offs

- [Risk] Pull-to-refresh を連続実行した場合に Toast が連続表示される。 → 1 回の読み込み試行につき 1 event に限定し、必要なら将来 debounce を検討する。
- [Risk] 初期ロード失敗時と手動更新失敗時の文脈が異なる。 → まずは同じ文言に統一し、将来必要なら文言を分ける。
- [Risk] 例外をログ出力することでノイズが増える。 → 読み込み失敗は調査価値が高いため `Timber.e` を使い、通常の成功/304 経路はログを増やさない。
- [Risk] 既存の画像保存 Toast とイベント名が混在する。 → 読み込みエラー用の UI event 型を分けるか、既存イベント名を拡張する場合も用途が分かる名前にする。

## Migration Plan

DB migration は不要。実装は ViewModel と Scaffold の UI event 配線を追加し、読み込み失敗分岐のログ出力と event 発行をテストで確認する。

## Open Questions

- Toast 文言を string resource 化するか、既存の固定文字列運用に合わせるか。
- 自動更新やバックグラウンド更新を将来追加する場合、同じ Toast 通知対象に含めるか。
