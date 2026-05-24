## Why

クラッシュやエラーが発生したときに、端末上の状態だけでは原因調査に必要な情報を取得しづらい。アプリ内ログをファイルとして保存し、ユーザー操作で共有できるようにすることで、Issue 478 の障害解析フローを改善する。

## What Changes

- アプリのログ出力をファイルにも保存する。
- 未捕捉例外が発生した場合に、クラッシュ情報をログファイルへ記録する。
- 「このアプリについて」画面に「ログを共有」項目を追加する。
- 保存済みログを FileProvider 経由の共有 Intent で他アプリへ渡せるようにする。
- ログが存在しない、または共有できない場合はクラッシュせずユーザーに状態を伝える。
- ログファイルの肥大化を避けるため、サイズ上限またはローテーションを設ける。

## Capabilities

### New Capabilities
- `log-file-sharing`: 保存済みログファイルを「このアプリについて」画面から共有するユーザー操作と共有時の振る舞いを扱う。

### Modified Capabilities
- `app-logging`: 既存の logging interface / Kermit ベースのログ基盤に、ファイル保存とクラッシュ情報記録の要件を追加する。

## Impact

- `SlevoApplication`: Kermit writer 設定と未捕捉例外 handler の初期化を拡張する。
- `core/log`: ファイル出力用 writer、ログファイル管理、共有用に参照するログファイル定義を追加する。
- `ui/about/AboutScreen.kt`: 「ログを共有」項目とクリック callback を追加する。
- `ui/navigation/AppNavGraph.kt`: About 画面の callback からログ共有処理を呼び出す。
- `ui/util`: ログ共有 Intent の組み立て、FileProvider URI 化、失敗時通知を担う helper を追加する。
- `app/src/main/res/xml/file_paths.xml`: 内部 files 領域のログディレクトリを FileProvider で共有可能にする。
- `app/src/main/res/values`: 「ログを共有」およびログなし/共有失敗時の文言を追加する。
- テスト: ファイルログ管理、クラッシュ記録、共有 Intent 生成または呼び出し境界の単体テストを追加する。
