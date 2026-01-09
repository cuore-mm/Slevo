# したらば掲示板（JBBS）取得仕様

このドキュメントは **「ユーザーが入力するHTMLのURL」** を起点に、  
アプリ（専ブラ）が **取得に適したエンドポイント** へ変換してデータを取得するための仕様をまとめる。

対象は以下の4系統：

- スレ一覧（`subject.txt`）
- 現行スレ本文（`rawmode.cgi`）
- 過去ログHTML（`read_archive.cgi`）
- 板設定（`api/setting.cgi`）

---

## 0. 前提：識別子とURLパーツ

- `dir` : カテゴリ（例：`movie`）
- `bbs` : 掲示板ID（例：`10948`）
- `dat` : スレッドID（例：`1767525739`）
- `option` : 取得範囲や件数の指定（`rawmode.cgi` / `read.cgi` で使用）

---

## 1. まずやること：URLの種類を判定する

アプリが受け取る可能性が高いURL（人間向け）を起点に、次のどれかに分類してから変換する。

### A. 板URL（HTML）
- `https://jbbs.shitaraba.net/<dir>/<bbs>/`

### B. 現行スレURL（HTML / read.cgi）
- `https://jbbs.shitaraba.net/bbs/read.cgi/<dir>/<bbs>/<dat>/<option?>`

### C. 過去ログURL（HTML / read_archive.cgi）
- `https://jbbs.shitaraba.net/bbs/read_archive.cgi/<dir>/<bbs>/<dat>/`

> 以降はこの分類ごとに「どこへ変換して何を取るか」を定義する。

---

## 2. URL変換の中心ルール（アプリ内部で統一して扱う）

### 2.1 板URL（HTML）→ スレ一覧（subject.txt）
ユーザーが板URLを開く/入力したとき、スレ一覧は `subject.txt` から取得する。

- 入力（板URL）  
  `https://jbbs.shitaraba.net/<dir>/<bbs>/`

- 取得（スレ一覧）  
  `https://jbbs.shitaraba.net/<dir>/<bbs>/subject.txt`

**変換規則**
- 「板URLの末尾に `subject.txt` を付与」  
  末尾スラッシュの有無は実装側で正規化してから付与する（`.../<bbs>/subject.txt` になるようにする）

---

### 2.2 現行スレURL（HTML / read.cgi）→ 本文（rawmode.cgi）
ユーザーが `read.cgi` のURLを開く/入力したとき、本文は `rawmode.cgi` で取得する。

- 入力（現行スレHTML）  
  `https://jbbs.shitaraba.net/bbs/read.cgi/<dir>/<bbs>/<dat>/<option?>`

- 取得（本文 / 専ブラ向け）  
  `https://jbbs.shitaraba.net/bbs/rawmode.cgi/<dir>/<bbs>/<dat>/<option?>`

**変換規則**
- `https://jbbs.shitaraba.net/bbs/read.cgi/`  
  を  
  `https://jbbs.shitaraba.net/bbs/rawmode.cgi/`  
  に置換する。

**例**
- HTML: `https://jbbs.shitaraba.net/bbs/read.cgi/movie/10948/1767525739/option`
- rawmode: `https://jbbs.shitaraba.net/bbs/rawmode.cgi/movie/10948/1767525739/option`

---

### 2.3 過去ログURL（read_archive.cgi）は変換しない
過去ログは `read_archive.cgi` のHTMLを取得してパースする。

- 取得（過去ログHTML）  
  `https://jbbs.shitaraba.net/bbs/read_archive.cgi/<dir>/<bbs>/<dat>/`

---

## 3. 取得フロー

### 3.1 板を開く（板URLが起点）
1) 入力URLが板URL（A）か判定  
2) `subject.txt` に変換して取得  
3) スレ一覧をパースして `dat` を得る  
4) スレURLを組み立てる場合は「アプリ内部表現」として `<dir>/<bbs>/<dat>` を保持しておく

---

### 3.2 現行スレを開く（read.cgi URLが起点）
1) 入力URLが現行スレ（B）か判定  
2) `rawmode.cgi` に変換して取得  
3) rawmodeレスポンスをパースして表示  
4) もし `ERROR: STORAGE IN`（倉庫）なら **過去ログ扱いへフォールバック**（3.3へ）

---

### 3.3 倉庫（過去ログ）を開く（read_archive.cgi が起点、または STORAGE IN から遷移）
1) `read_archive.cgi` で取得  
2) HTMLをパースして表示

---

## 4. エンドポイント仕様

### 4.1 スレ一覧（subject.txt）

**URL**
- `https://jbbs.shitaraba.net/<dir>/<bbs>/subject.txt`

**文字コード**
- EUC-JP（実装側で確実にデコードする）

**フォーマット**
- 1行につき1スレッド  
- 書式：`[スレッド番号].cgi,[スレッドタイトル](レス数)`

**例**
```
1767525739.cgi,興行収入を見守るスレ したらば別室623(396)
1766802272.cgi,避難所板 雑談スレッド ★18(183)
```

**パース指針**
- `,` で2分割（左：`xxxx.cgi`、右：`タイトル(レス数)`）
- 左側は `.cgi` を除去して `dat` として扱う
- 右側末尾の `(数字)` をレス数として抽出、残りをタイトル

---

### 4.2 現行スレ本文（rawmode.cgi / dat風）

**URL**
- `https://jbbs.shitaraba.net/bbs/rawmode.cgi/<dir>/<bbs>/<dat>/<option?>`

**option（取得範囲）**
- 単一レス：`NN`（例：`.../123`）
- 範囲（終端あり）：`XX-YY`（例：`.../123-125`）
- 範囲（終端なし）：`NN-`（例：`.../250-`）
- 最新NN件：`lNN`（先頭は小文字l）（例：`.../l50`）

**レスポンス**
- 1レス = 1行  
- 区切り：`<>`  
- 改行：LF

**1行のフォーマット**
`[レス番号]<>[名前]<>[メール]<>[日付]<>[本文]<>[スレッドタイトル]<>[ID]`

**重要な挙動**
- スレッドタイトルは **1番レスにのみ入る**のが基本  
  → `2-` や `l50` のように1番を含まない取得ではタイトルが欠落し得る  

**本文・名前の整形**
- `名前` にHTMLタグが混ざることがある
- `本文` は `<br>` 改行やHTMLエンティティ（例：`&#65374;`）を含むことがある
- 推奨整形：
  - `<br>` → `\n`
  - HTMLエンティティをデコード
  - タグ除去（リンク/アンカーを保持するかはUI方針で決める）

**エラー通知**
- 失敗時は **HTTPレスポンスヘッダの `ERROR:`** に出ることがある（本文だけ見ない）

代表例：
- `ERROR: BBS NOT FOUND`（`bbs` 不正）
- `ERROR: KEY NOT FOUND`（`dat` 不正）
- `ERROR: THREAD NOT FOUND`（スレ削除等）
- `ERROR: STORAGE IN`（倉庫＝過去ログ扱い）

---

### 4.3 過去ログ（read_archive.cgi / HTML）

**URL**
- `https://jbbs.shitaraba.net/bbs/read_archive.cgi/<dir>/<bbs>/<dat>/`

**HTML構造（典型）**
- レスは `<dl>` 内に `dt`（ヘッダ）と `dd`（本文）のペアで並ぶ
  - `dt`：レス番号・名前・日付・IDなど
  - `dd`：本文（`<br>` 改行、リンクやアンカーを含む）

**パース方針（実装案）**
1) `dl > dt` と `dl > dd` を配列で取得し、同じindexでペアにする  
2) `dt` からレス番号/名前/メール/日付/ID を抽出（揺れに強い正規表現にする）  
3) `dd` は本文として整形  
   - `<br>` → `\n`  
   - タグの扱い方針を決める（改行・リンク・アンカーなど）

---

### 4.4 掲示板設定（SETTING.TXT相当 / api/setting.cgi）

**URL**
- `https://jbbs.shitaraba.net/bbs/api/setting.cgi/<dir>/<bbs>/`

**文字コード**
- EUC-JP

**フォーマット**
- LF区切り、1行が `KEY=VALUE`  
- **最初の `=` までがKEY**（VALUEに `=` が含まれる可能性あり）

**主なキー（例）**
- `TOP`：掲示板URL
- `DIR`：カテゴリ
- `BBS`：掲示板番号
- `CATEGORY`：カテゴリ名
- `BBS_THREAD_STOP`：最大レス数
- `BBS_NONAME_NAME`：デフォ名無し
- `BBS_DELETE_NAME`：あぼーん名
- `BBS_TITLE`：板タイトル
- `BBS_COMMENT`：板説明文

**エラー**
- 本文が `ERROR=...` の形式で返ることがある  
  → 本文先頭一致で判定できるようにしておく
  - 例：板URLは常に `.../<bbs>/` として扱ってから `subject.txt` を付与する
