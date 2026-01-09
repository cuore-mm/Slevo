# 5ch URL 構造と取得仕様

このドキュメントでは、5ch.net のURL構造と、専用ブラウザ（専ブラ）がデータを取得するための仕様をまとめる。

## 1. URL パターン一覧

以下は 5ch.net 関連で登場する主要なURL形式である。

### 一般的なブラウザ向け (HTML)
*   **板トップ**
    *   `https://<server>.5ch.net/<board>/`
*   **スレッド (現行)**
    *   `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/<option?>`
*   **スレッド (過去ログ)**
    *   `https://kako.5ch.net/test/read.cgi/<board>/<threadKey>/<option?>`

### スマートフォンブラウザ向け (itest)
*   **板トップ (subback)**
    *   `https://itest.5ch.net/subback/<board>`
*   **スレッド**
    *   `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/<option?>`

### 専用ブラウザ・データ取得向け
*   **スレッド一覧 (subject.txt)**
    *   `https://<server>.5ch.net/<board>/subject.txt`
*   **板設定 (SETTING.TXT)**
    *   `https://<server>.5ch.net/<board>/SETTING.TXT`
*   **スレッドデータ (dat - 現行)**
    *   `https://<server>.5ch.net/<board>/dat/<threadKey>.dat`
*   **スレッドデータ (dat - 過去ログ/oyster)**
    *   `https://<server>.5ch.net/<board>/oyster/<threadKey上位4桁>/<threadKey>.dat`

---

## 2. データ取得仕様詳細

このセクションでは **「ユーザーが入力するHTMLのURL」** を起点に、  
アプリ（専ブラ）が **取得に適したエンドポイント** へ変換してデータを取得するための詳細仕様をまとめる。

対象は以下の系統：

- スレ一覧（`subject.txt`）
- 現行スレ本文（`dat`）
- 過去ログ本文（`oyster` の `dat`）
- 板設定（`SETTING.TXT`）

---

### 2.0 前提：識別子とURLパーツ

- `host` : サーバ（例：`agree.5ch.net`）
- `board` : 板キー（例：`operate`）
- `threadKey` : スレッドID（UNIX時刻由来の数値文字列）
- `option` : `read.cgi` の表示オプション（取得では使用しない）
- `prefix` : 過去ログ（`oyster`）のディレクトリ名（`threadKey` 先頭4桁）

---

### 2.1 まずやること：URLの種類を判定する

アプリが受け取る可能性が高いURL（人間向け）を起点に、次のどれかに分類してから変換する。

#### A. 板URL（HTML）
- `https://<host>/<board>/`

#### B. 現行スレURL（HTML / read.cgi）
- `https://<host>/test/read.cgi/<board>/<threadKey>/<option?>`

#### C. DAT URL（専ブラ向け）
- `https://<host>/<board>/dat/<threadKey>.dat`

> 以降はこの分類ごとに「どこへ変換して何を取るか」を定義する。

---

### 2.2 URL変換の中心ルール（アプリ内部で統一して扱う）

#### 2.2.1 板URL（HTML）→ スレ一覧（subject.txt）
ユーザーが板URLを開く/入力したとき、スレ一覧は `subject.txt` から取得する。

- 入力（板URL）  
  `https://<host>/<board>/`

- 取得（スレ一覧）  
  `https://<host>/<board>/subject.txt`

**変換規則**
- 「板URLの末尾に `subject.txt` を付与」  
  末尾スラッシュの有無は実装側で正規化してから付与する（`.../<board>/subject.txt` になるようにする）

---

#### 2.2.2 現行スレURL（HTML / read.cgi）→ 本文（dat）
ユーザーが `read.cgi` のURLを開く/入力したとき、本文は `dat` で取得する。

- 入力（現行スレHTML）  
  `https://<host>/test/read.cgi/<board>/<threadKey>/<option?>`

- 取得（本文 / 専ブラ向け）  
  `https://<host>/<board>/dat/<threadKey>.dat`

**変換規則**
- `host` / `board` / `threadKey` を抽出し、`dat` URL を再構成する  
  `option` は取得には不要のため破棄する

---

#### 2.2.3 DAT URL（専ブラ向け）は変換しない
DAT URL を直接入力された場合は、そのまま取得する。

---

#### 2.2.4 過去ログは `oyster` へフォールバック
`dat` 取得に失敗した場合、過去ログとして `oyster` を試す。

- 取得（過去ログ dat）  
  `https://<host>/<board>/oyster/<prefix>/<threadKey>.dat`

**変換規則**
- `prefix` は `threadKey` の先頭4桁（4桁未満なら全体）  
  例：`threadKey = 1234567890` → `prefix = 1234`

---

## 3. 取得フロー

### 3.1 板を開く（板URLが起点）
1) 入力URLが板URL（A）か判定  
2) `subject.txt` に変換して取得  
3) スレ一覧をパースして `threadKey` を得る  
4) スレURLを組み立てる場合は「アプリ内部表現」として `<host>/<board>/<threadKey>` を保持しておく

---


### 3.2 現行スレを開く（read.cgi URLが起点）
1) 入力URLが現行スレ（B）か判定  
2) `dat` に変換して取得  
3) datレスポンスをパースして表示  
4) 取得失敗時は **`oyster` へフォールバック**（3.3へ）

---

### 3.3 過去ログを開く（`oyster` が起点、またはフォールバック）
1) `oyster` の `dat` を取得  
2) datレスポンスをパースして表示

---


## 4. エンドポイント仕様

### 4.1 スレ一覧（subject.txt）

**URL**
- `https://<host>/<board>/subject.txt`

**文字コード**
- Shift_JIS

**フォーマット**
- 1行につき1スレッド  
- 書式：`[スレッド番号].dat<>[スレッドタイトル] ([レス数])`

**例**
```
1767525739.dat<>スレタイ例 (123)
1766802272.dat<>雑談スレッド ★18 (456)
```

**パース指針**
- `<>` で2分割（左：`xxxx.dat`、右：`タイトル (レス数)`）  
- 左側は `.dat` を除去して `threadKey` として扱う  
- 右側末尾の `(数字)` をレス数として抽出、残りをタイトル  
- タイトルは HTML を含む場合があるためデコードする

---


### 4.2 現行スレ本文（dat）

**URL**
- `https://<host>/<board>/dat/<threadKey>.dat`

**文字コード**
- Shift_JIS

**レスポンス**
- 1レス = 1行  
- 区切り：`<>`  
- 改行：LF

**1行のフォーマット**
`[名前]<>[メール]<>[日付+ID等]<>[本文]<>[スレッドタイトル]`

**重要な挙動**
- スレッドタイトルは **1番レスにのみ入る**のが基本  
  → `1` を含まない差分取得ではタイトルが欠落し得る
- `日付+ID等` には `ID:xxxxx` や `BE:xxxx-xxxxx` が混在することがある

**本文・名前の整形**
- `名前` にHTMLタグが混ざることがある  
- `本文` は `<br>` 改行やHTMLエンティティ（例：`&amp;`）を含むことがある  
- 推奨整形：  
  - `<br>` → `
`  
  - HTMLエンティティをデコード  
  - タグ除去（リンク/アンカーを保持するかはUI方針で決める）

---


### 4.3 過去ログ本文（oyster / dat）

**URL**
- `https://<host>/<board>/oyster/<prefix>/<threadKey>.dat`

**文字コード / フォーマット**
- 現行 `dat` と同様

---


### 4.4 掲示板設定（SETTING.TXT）

**URL**
- `https://<host>/<board>/SETTING.TXT`

**文字コード**
- Shift_JIS

**フォーマット**
- LF区切り、1行が `KEY=VALUE`  
- **最初の `=` までがKEY**（VALUEに `=` が含まれる可能性あり）

**主なキー（例）**
- `BBS_TITLE_ORIG`：板タイトル（優先）
- `BBS_TITLE`：板タイトル（フォールバック）
- `BBS_NONAME_NAME`：デフォ名無し
- `BBS_THREAD_STOP`：最大レス数
- `BBS_DELETE_NAME`：あぼーん名無し
