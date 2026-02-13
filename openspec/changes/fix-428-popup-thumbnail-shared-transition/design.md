## Context

Issue #428 では、レスポップアップを 2 段以上重ねたときに画像サムネイルの中身が描画されず、枠のみ表示される。現在の shared transition キーは `"<url>#<index>"` で構成されており、ポップアップ階層や投稿コンテキストを含まないため、同一 URL を含む複数コンポーネントが同時表示されると衝突しやすい。

本変更は、スレッド一覧・ポップアップ・画像ビューアの 3 箇所にまたがる shared transition 契約を揃えるクロスカット変更である。表示品質を維持しつつ、既存の画像遷移体験を壊さないことが制約となる。

## Goals / Non-Goals

**Goals:**
- ポップアップ多段表示でも画像サムネイルを常に描画できるようにする。
- ポップアップ起点でも shared transition を維持し、画像ビューア遷移時の整合性を保つ。
- 既存の初期表示インデックス・同一レス内画像切替契約を維持する。

**Non-Goals:**
- shared transition ライブラリの置き換えや描画エンジン変更。
- 画像ビューアの UI/UX（操作体系、レイアウト、メニュー構成）変更。
- 画像 URL 抽出・保存・共有など画像アクション機能の仕様変更。

## Decisions

1. 遷移キーを「表示文脈付き」で一意化する。
   - 決定: shared key を `"<transitionNamespace>|<url>|<index>"` 形式に統一する。
   - 理由: URL とインデックスだけではポップアップ階層衝突を防げないため。
   - 代替案: ランダム UUID を都度付与する案は、戻り遷移でキー一致を保証しづらく不採用。

2. transitionNamespace を遷移イベントで ImageViewer まで運ぶ。
   - 決定: サムネイルタップイベントに namespace を含め、`AppRoute.ImageViewer` へ引き渡す。
   - 理由: 遷移元と遷移先で同一キーを再構築するために必要。
   - 代替案: グローバル状態で namespace を保持する案は、タブ遷移や多重遷移時の取り違えリスクがあるため不採用。

3. namespace 生成は表示コンポーネント側で行う。
   - 決定: 通常リストとポップアップで namespace 生成規則を明示し、`PostItem` 系に受け渡す。
   - 理由: 表示文脈（通常表示/ポップアップ階層）を最も正確に把握できる層だから。
   - 代替案: `ImageThumbnailGrid` 内で自動推定する案は、階層情報を取得できず不採用。

## Risks / Trade-offs

- [Risk] namespace 伝播漏れで一部経路のみ shared transition が不一致になる
  → Mitigation: 画像遷移呼び出し箇所（Thread/Board/PostDialog）を網羅的に確認し、既定値を設ける。
- [Risk] ルート引数追加に伴い既存遷移コードのコンパイルエラーが発生する
  → Mitigation: `AppRoute.ImageViewer` に後方互換なデフォルト値を設け、段階的に呼び出し側を更新する。
- [Trade-off] キーが長くなり可読性はやや低下する
  → Mitigation: キー生成を共通化し、文字列フォーマットを 1 箇所で管理する。

## Migration Plan

1. `AppRoute.ImageViewer` に namespace パラメータを追加し、既定値を設定する。
2. サムネイル側キー生成を新フォーマットへ変更する。
3. Thread/Popup 経路で namespace を生成して遷移へ渡す。
4. ImageViewer 側キー生成を新フォーマットへ切り替える。
5. 多段ポップアップ再現手順で描画と遷移の整合を確認する。

ロールバック時は、キー生成を旧形式 `"<url>#<index>"` に戻し、追加した namespace 引数を未使用化する。

## Open Questions

- namespace を共通ユーティリティ化するか、まずはスレッド画面ローカル実装に留めるか。
- ポップアップ階層識別に index を使う場合、将来的な並び替え要件に備え識別子導入が必要か。
