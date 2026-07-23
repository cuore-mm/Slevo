## Why

起動時のpending restoreは成功・失敗結果を`restore-result.json`へ記録するが、production UIにはその結果を読み取り、表示し、通知済みとして削除するconsumerがない。復元後にユーザーが成否を確認できず、未消費resultが次回の復元準備まで残るため、画面に依存しない起動通知を追加する。

## What Changes

- アプリ全体を包むroot-level `SnackbarHost`を追加し、起動時restoreの最終的な成功・失敗を現在のrouteに関係なく通知する。
- app-levelの`UiState` ownerがpending restore markerとresultを読み取り、中間状態を通知せず、通知可能な最終結果だけを保持する。
- Snackbarへ結果を引き渡した後に明示的なacknowledge処理を行い、対応するresult fileを削除して同じ結果を重複通知しない。
- UI consumerの開始前、Room migration完了待ち、画面遷移、構成変更、Snackbar表示前のprocess終了を考慮したone-shot lifecycleを定義する。
- result欠損・malformed・削除失敗時の安全なfallbackとログ記録を追加し、restore state machineやアプリ起動を壊さない。
- ViewModel、root Compose UI、result lifecycleのunit/Compose testsを追加する。

## Capabilities

### New Capabilities

- `pending-restore-result-notification`: 起動時restoreの確定結果をアプリ全体のSnackbarで一度通知し、通知済みresultを安全に消費する契約。

### Modified Capabilities

- なし

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/MainActivity.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/AppScaffold.kt`
- 新しいapp-level restore result `ViewModel` / `UiState`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/`配下のresult読取・acknowledge境界
- root-level Snackbarとresult lifecycleを検証するunit/Compose tests
- Room schema、backup archive形式、pending marker/result JSON schema、restore適用順序は変更しない。
