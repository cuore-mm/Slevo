## Why

一部のユーザー環境で、投稿本文や名前に絵文字などの Shift_JIS 非対応文字を含めると、5ch 側で文字化けして意図しない内容で保存される。投稿機能の信頼性に直結するため、投稿・スレ立ての両経路で文字化けを防ぐ仕様を明確化して早期に修正する必要がある。

## What Changes

- 投稿フォーム送信前に、Shift_JIS で表現できない文字を数値文字参照（NCR）へ変換する仕様を追加する（`MESSAGE` だけでなく `name`/`mail`/`subject` を含む）。
- 返信投稿・スレ立て投稿の一次送信/確認送信のすべてで、同一変換ルールを適用する。
- URL エンコード済み文字列を直接渡す経路ではなく、フォームビルダーに未エンコード文字列を渡して送信時のエンコードを一元化する。
- 変換対象外（Shift_JIS で表現可能）文字は原文を保持し、既存の投稿内容互換性を維持する。

## Capabilities

### New Capabilities
- `post-request-encoding`: 投稿送信時に Shift_JIS 非対応文字を破損させずに送信するための文字変換・フォームエンコード契約を定義する。

### Modified Capabilities
- なし

## Impact

- 影響コード: `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/remote/impl/PostRemoteDataSourceImpl.kt`
- 影響コード: `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/remote/impl/ThreadCreateRemoteDataSourceImpl.kt`
- 追加予定ユーティリティ: 文字列変換（NCR 化）ヘルパー
- 外部 API 影響: なし（5ch 投稿 API のフォームパラメータ契約は維持）
