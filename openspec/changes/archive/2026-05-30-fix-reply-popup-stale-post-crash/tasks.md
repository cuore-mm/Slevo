## 1. 状態モデルの投稿番号化

- [x] 1.1 `PopupInfo` の表示対象を `posts: List<ThreadPostUiModel>` から `postNumbers: List<Int>` へ変更し、KDoc を投稿番号ベースの責務に更新する
- [x] 1.2 `PopupInfo` を生成する Preview、テストデータ、補助関数を投稿番号ベースへ更新する
- [x] 1.3 ポップアップ連続同一表示の判定を、投稿 UI モデル比較ではなく投稿番号リスト・インデント・ルート番号の比較に変更する

## 2. ViewModel のポップアップ生成更新

- [x] 2.1 `addPopupForReplyNumber` が検証済み投稿番号を `PopupInfo.postNumbers` に保存するよう変更する
- [x] 2.2 `addPopupForReplyFrom` が NG 除外後の返信番号リストを `PopupInfo.postNumbers` と `rootNumbers` に整合させて保存するよう変更する
- [x] 2.3 `addPopupForId` が最新投稿一覧から ID 一致投稿番号を抽出し、投稿番号リストとして保存するよう変更する
- [x] 2.4 `addPopupForTree` がツリー選択結果の投稿番号、インデント、ルート番号を同じ順序で保存するよう変更する

## 3. ポップアップ描画の最新状態解決

- [x] 3.1 `PopupPostLazyColumn` で `info.postNumbers` から最新 `posts` を `getOrNull(postNumber - 1)` で解決する行モデルを作成する
- [x] 3.2 解決できない投稿番号または NG 投稿は描画対象から除外し、空になった場合も範囲外アクセスやクラッシュを起こさないようにする
- [x] 3.3 `idIndexList` は `postNumber - 1` と `getOrElse` で安全に取得し、負の添字・範囲外添字アクセスを排除する
- [x] 3.4 返信元、ID 集計、my 投稿、画像 shared transition、タップ基準座標の計算が投稿番号ベースで従来どおり動作することを確認しながら更新する
- [x] 3.5 divider とツリーインデントが、スキップされた投稿の有無にかかわらず表示行の順序と整合するよう調整する

## 4. 回帰テストと検証

- [x] 4.1 ポップアップ表示中に `posts` が別インスタンスへ差し替わってもクラッシュせず、投稿番号から最新投稿を表示するテストを追加する
- [x] 4.2 `idIndexList` が短い場合や対象投稿番号が範囲外の場合に `IndexOutOfBoundsException` が発生しないテストを追加する
- [x] 4.3 返信番号、返信元、ID、ツリーポップアップの既存 ViewModel テストを投稿番号ベースに更新し、連続同一表示抑止も確認する
- [x] 4.4 既存のポップアップ配置、高さ、スクロールバー、shared transition 関連テストを実行して回帰がないことを確認する
- [x] 4.5 アプリ全体のビルドと単体テストを実行し、issue 488 のクラッシュ再現手順でクラッシュしないことを確認する
