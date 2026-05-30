## Why

タブ一覧のスレッド詳細 BottomSheet では、スレッドキーから導出できる作成日時と勢いが補完されず、日付が初期値のまま表示される。既存の板一覧、スレッド画面、subject.txt パース処理には同種の計算が分散しているため、計算規則を共通化してタブ詳細でも同じ情報を表示できるようにする。

## What Changes

- スレッドキーとレス数から、スレッド作成日時、勢い、有効な epoch thread key 判定を導出する共通ユーティリティを追加する。
- 板一覧、スレッド画面、subject.txt パース処理で分散している日付・勢い計算を共通ユーティリティへ置き換える。
- タブ一覧のスレッド詳細 BottomSheet 用 `ThreadInfo` 生成時に、共通ユーティリティで日付と勢いを補完する。
- 無効な thread key や `THREAD_KEY_THRESHOLD` 以上の key では、既存同様にデフォルト日付と勢い `0.0` を使う。
- DB スキーマや永続化項目は変更しない。

## Capabilities

### New Capabilities
- `thread-derived-info`: スレッドキーとレス数から作成日時・勢いなどの表示用派生情報を一貫して導出する機能。

### Modified Capabilities
- `tablist-ui`: タブ一覧のスレッド詳細 BottomSheet で、対象スレッドの thread key とレス数から補完した作成日時・勢いを表示する要件を追加する。

## Impact

- 影響範囲:
  - `data/util`: スレッド派生情報計算ユーティリティの追加または既存計算の移動
  - `ThreadListParser`: subject.txt パース時の日付・勢い計算の置き換え
  - `BoardRepository`: 板一覧表示モデル生成時の日付・勢い計算の置き換え
  - `ThreadViewModel`: スレッド読み込み成功時の日付・勢い計算の置き換え
  - `TabScreenContent` / `TabDetailBottomSheets`: タブ詳細シート用 `ThreadInfo` の補完
  - 単体テスト: 派生情報計算の境界条件とタブ詳細補完の確認
- DB migration は不要。
- UI 仕様は既存の `ThreadInfoBottomSheet` 表示構造を維持し、表示値だけを補完する。
