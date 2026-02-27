## Why

現在の画像アクションUIは、画像の読み込み状態に関係なく同一メニューを表示するため、読み込み失敗時や読み込み中でも保存・共有など不適切な操作を実行できてしまう。Issue #455 では、失敗種別（404/410 とその他）および読み込み中の状態に応じて実行可能なアクションを明確に制御し、誤操作を防ぐ必要がある。

加えて現状の `読み込みを中止` はUI状態のみを中止扱いにしており、実リクエストが継続してキャッシュ完了した場合に、スレッド画面と画像ビューア画面で表示が乖離する。中止操作は実リクエスト中断と同時に、画面間で同一状態を参照する必要がある。

## What Changes

- スレッド画面の画像メニューで、対象画像の読み込み状態（成功/404-410失敗/その他失敗/読み込み中）ごとに表示アクションを切り替える。
- 画像ビューア画面で、上部メニューの表示アクションを同じ状態分類で切り替える。
- 画像ビューア画面では、読み込み中のときのみ「読み込みを中止」をトップバーのアイコンアクションとして表示する。
- 「読み込みを中止」実行時は対象画像の実リクエストを中断し、失敗時と同様にリトライ導線を表示できる状態へ遷移させる。
- スレッド画面と画像ビューア画面が同一画像URLに対して同一の読み込み状態を参照するよう、状態管理を単一ソース化する。
- 非同期コールバック競合（中止後の成功/失敗通知）で状態を巻き戻さないよう、世代管理付きで古い通知を無効化する。
- 既存の成功時アクション契約（保存・コピー・共有・外部起動・検索・一括保存）と既存のアクション実行共通化契約を維持する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `thread-image-menu`: スレッド画面の画像メニュー表示を読み込み状態連動に変更し、失敗種別と読み込み中の状態で表示可能アクションを制限する要件を追加する。
- `image-viewer`: 画像ビューアのメニューおよびトップバーアクションを読み込み状態連動に変更し、読み込み中の中止アクションを追加する要件を追加する。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/sheet/ImageMenuSheet.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerTopBar.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerViewModel.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/common/ImageMenuActionRunner.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/common/*`（読み込み状態単一ソース/中止制御の共通化）
- `app/src/main/res/values/strings_image.xml`
- OpenSpec 仕様更新: `openspec/specs/thread-image-menu/spec.md`, `openspec/specs/image-viewer/spec.md`
