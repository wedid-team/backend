"""ai_review.py의 순수 함수(네트워크·git 호출 없음)에 대한 단위 테스트.

실행 (레포 맨 위 폴더에서):
    python .github/scripts/test_ai_review.py -v
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))

from ai_review import collect, make_chunks, parse_diff  # noqa: E402


class ParseDiffTests(unittest.TestCase):
    def test_added_removed_context_lines(self):
        diff = (
            "diff --git a/foo.py b/foo.py\n"
            "index e69de29..4b825dc 100644\n"
            "--- a/foo.py\n"
            "+++ b/foo.py\n"
            "@@ -1,3 +1,4 @@\n"
            " line1\n"
            "-line2\n"
            "+line2 changed\n"
            "+line3 new\n"
            " line4\n"
        )
        files = parse_diff(diff)
        self.assertEqual(len(files), 1)
        self.assertEqual(files[0]["path"], "foo.py")
        # 추가 줄(2,3)과 그대로인 줄(1,4)은 댓글 가능. 삭제된 줄은 번호가 없어 포함되지 않음
        self.assertEqual(files[0]["commentable"], {1, 2, 3, 4})

    def test_multiple_hunks_continue_line_numbers_independently(self):
        diff = (
            "diff --git a/bar.py b/bar.py\n"
            "--- a/bar.py\n"
            "+++ b/bar.py\n"
            "@@ -1,2 +1,2 @@\n"
            " line1\n"
            "+line1b\n"
            "@@ -10,1 +11,2 @@\n"
            " line10\n"
            "+line11\n"
        )
        files = parse_diff(diff)
        self.assertEqual(len(files), 1)
        # 두 번째 hunk는 새 시작 줄번호(11)부터 다시 매겨짐
        self.assertEqual(files[0]["commentable"], {1, 2, 11, 12})

    def test_deleted_file_is_excluded(self):
        diff = (
            "diff --git a/old.py b/old.py\n"
            "deleted file mode 100644\n"
            "--- a/old.py\n"
            "+++ /dev/null\n"
            "@@ -1,2 +0,0 @@\n"
            "-line1\n"
            "-line2\n"
        )
        self.assertEqual(parse_diff(diff), [])

    def test_skip_files_excluded_even_with_changes(self):
        diff = (
            "diff --git a/gradlew b/gradlew\n"
            "--- a/gradlew\n"
            "+++ b/gradlew\n"
            "@@ -1,1 +1,1 @@\n"
            "-old\n"
            "+new\n"
        )
        self.assertEqual(parse_diff(diff), [])


class MakeChunksTests(unittest.TestCase):
    @staticmethod
    def _file(path, line_count):
        return {"path": path, "rows": [f"row {i}" for i in range(line_count)]}

    def test_large_file_is_split_into_multiple_pieces(self):
        files = [self._file("big.py", 10)]
        texts, skipped = make_chunks(files, chunk_lines=4, max_calls=10)
        # 10줄짜리 파일이 chunk_lines=4 단위(4+4+2)로 조각나고, 서로 못 합쳐져 조각마다 묶음이 됨
        self.assertEqual(len(texts), 3)
        self.assertEqual(skipped, [])

    def test_max_calls_limits_and_reports_skipped_paths(self):
        files = [self._file(p, 3) for p in ("a.py", "b.py", "c.py")]
        texts, skipped = make_chunks(files, chunk_lines=5, max_calls=2)
        self.assertEqual(len(texts), 2)
        self.assertEqual(skipped, ["c.py"])


class CollectTests(unittest.TestCase):
    def test_dedup_commentable_and_default_severity(self):
        files = [{"path": "a.py", "commentable": {1, 2, 3}}]
        answers = [{
            "summary": "요약",
            "comments": [
                {"path": "a.py", "line": 2, "severity": "High", "title": "t1", "body": "b1"},
                {"path": "a.py", "line": 2, "severity": "High", "title": "t1", "body": "dup"},  # 중복, 제외
                {"path": "a.py", "line": 99, "severity": "bogus", "title": "t2", "body": "b2"},  # 댓글 불가 줄
            ],
        }]
        summary, comments, extra = collect(answers, files)
        self.assertEqual(summary, "요약")
        self.assertEqual(len(comments), 1)
        self.assertEqual(comments[0]["line"], 2)
        self.assertEqual(len(extra), 1)
        self.assertIn("a.py:99", extra[0])
        self.assertIn("Medium", extra[0])  # 잘못된 severity는 Medium으로 대체


if __name__ == "__main__":
    unittest.main()
