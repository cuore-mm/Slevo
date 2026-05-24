## Why

板画面ではボトムバーのタイトルカードをタップしても板の詳細情報を確認できず、既存の板情報ダイアログも操作導線に接続されていない。スレッド画面と同じ情報シート体験を板画面にも提供し、板URLのコピー・外部ブラウザ表示・共有をすばやく実行できるようにする。

## What Changes

- 板画面のボトムバータイトルカードをタップしたとき、板情報を表示する `BoardInfoBottomSheet` を開く。
- `BoardInfoBottomSheet` には板名をタイトルとして表示し、その下に板のサービス名を表示する。
- `BoardInfoBottomSheet` のアクションはコピー、外部ブラウザで開く、共有のみを表示する。
- `ThreadInfoBottomSheet` のタイトル・サブ情報・アクションボタン領域を共通化し、スレッド情報シートと板情報シートの双方から利用できる構造にする。
- 既存の未接続な板情報ダイアログ表示状態は、板情報ボトムシートの表示状態へ置き換える。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `board-thread-info-sheet`: 板画面でスレッド情報だけでなく、ボトムバータイトルカードから板情報ボトムシートを表示できる要件を追加する。

## Impact

- 影響範囲: `BoardScaffold`, `BoardViewModel`, `BoardUiState`, `ThreadInfoBottomSheet`, 新規 `BoardInfoBottomSheet`, 共通情報シートUIコンポーネント。
- UI状態: 板情報ダイアログ用の状態を板情報シート用の状態へ整理する。
- テスト: 板情報シートの開閉状態を扱う ViewModel 単体テストと、必要に応じて共通UIのプレビューを追加する。
- 外部依存や永続化スキーマの変更はない。
